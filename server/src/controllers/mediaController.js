const MediaModel = require('../models/mediaModel');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

function getAuthorizedImportDir() {
    const dir = process.env.LOCAL_IMPORT_DIR
        ? path.resolve(process.env.LOCAL_IMPORT_DIR)
        : path.join(__dirname, '../../uploads/import');
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
    return dir;
}

const ALLOWED_IMPORT_EXTENSIONS = new Set(['.mp4', '.mkv', '.avi', '.mov', '.webm', '.m4v']);

function scanImportFolder(dirPath, baseDir) {
    let results = [];
    if (!fs.existsSync(dirPath)) return results;

    const entries = fs.readdirSync(dirPath, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = path.join(dirPath, entry.name);
        const resolvedPath = path.resolve(fullPath);

        if (!resolvedPath.startsWith(baseDir)) {
            continue;
        }

        if (entry.isDirectory()) {
            results = results.concat(scanImportFolder(fullPath, baseDir));
        } else if (entry.isFile()) {
            const ext = path.extname(entry.name).toLowerCase();
            if (ALLOWED_IMPORT_EXTENSIONS.has(ext)) {
                try {
                    const stat = fs.statSync(resolvedPath);
                    const relativePath = path.relative(baseDir, resolvedPath).replace(/\\/g, '/');
                    const fileId = crypto.createHash('sha256').update(relativePath).digest('hex').substring(0, 32);
                    results.push({
                        fileId,
                        fileName: entry.name,
                        relativePath,
                        fullPath: resolvedPath,
                        fileSize: stat.size,
                        fileFormat: ext.replace('.', '').toLowerCase()
                    });
                } catch (e) {}
            }
        }
    }
    return results;
}

const MediaController = {
    async checkAvailability(req, res) {
        try {
            const { tmdbId } = req.params;
            const media = await MediaModel.findByTmdbId(tmdbId);
            if (!media) {
                return res.status(200).json({ available: false, message: 'Película no disponible en el servidor.' });
            }
            return res.status(200).json({ available: true, media });
        } catch (err) {
            console.error('Error al comprobar disponibilidad:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async listImportableFiles(req, res) {
        try {
            const baseDir = getAuthorizedImportDir();
            const scannedFiles = scanImportFolder(baseDir, baseDir);
            const allMedia = await MediaModel.findAll();

            const registeredPathsMap = new Map();
            for (const item of allMedia) {
                if (item.storage_path) {
                    const normalizedStoragePath = path.resolve(item.storage_path);
                    registeredPathsMap.set(normalizedStoragePath, item);
                }
            }

            const files = scannedFiles.map(file => {
                const registeredMedia = registeredPathsMap.get(file.fullPath);
                return {
                    fileId: file.fileId,
                    fileName: file.fileName,
                    relativePath: file.relativePath,
                    fileSize: file.fileSize,
                    fileFormat: file.fileFormat,
                    isRegistered: Boolean(registeredMedia),
                    registeredMedia: registeredMedia ? {
                        id: registeredMedia.id,
                        tmdb_id: registeredMedia.tmdb_id,
                        display_title: registeredMedia.display_title || registeredMedia.file_name
                    } : null
                };
            });

            return res.status(200).json({
                importDir: 'uploads/import',
                filesCount: files.length,
                files
            });
        } catch (err) {
            console.error('Error al listar archivos para importación local:', err);
            return res.status(500).json({ error: 'Error interno del servidor al listar archivos locales.' });
        }
    },

    async importFiles(req, res) {
        try {
            const baseDir = getAuthorizedImportDir();
            const scannedFiles = scanImportFolder(baseDir, baseDir);

            const fileMap = new Map();
            for (const sf of scannedFiles) {
                fileMap.set(sf.fileId, sf);
            }

            let itemsToImport = [];
            if (Array.isArray(req.body.items)) {
                itemsToImport = req.body.items;
            } else if (req.body.fileId) {
                itemsToImport = [req.body];
            }

            if (!itemsToImport.length) {
                return res.status(400).json({ error: 'No se han seleccionado archivos para importar.' });
            }

            const results = [];
            const allMedia = await MediaModel.findAll();
            const registeredPathsMap = new Map();
            for (const item of allMedia) {
                if (item.storage_path) {
                    registeredPathsMap.set(path.resolve(item.storage_path), item);
                }
            }

            for (const item of itemsToImport) {
                const { fileId, tmdbId, mediaType, displayTitle } = item;
                const targetFile = fileMap.get(fileId);

                if (!targetFile) {
                    results.push({
                        fileId,
                        success: false,
                        error: 'Archivo no encontrado en la carpeta autorizada del servidor.'
                    });
                    continue;
                }

                if (!targetFile.fullPath.startsWith(baseDir) || !fs.existsSync(targetFile.fullPath)) {
                    results.push({
                        fileId,
                        fileName: targetFile.fileName,
                        success: false,
                        error: 'Ruta no válida o el archivo ya no existe.'
                    });
                    continue;
                }

                const existing = registeredPathsMap.get(targetFile.fullPath);
                if (existing) {
                    results.push({
                        fileId,
                        fileName: targetFile.fileName,
                        success: true,
                        isDuplicate: true,
                        message: 'El archivo ya estaba registrado en la biblioteca.',
                        media: existing
                    });
                    continue;
                }

                const resolvedMediaType = (mediaType && mediaType !== 'null' && mediaType !== 'undefined') ? mediaType : (tmdbId ? 'movie' : 'independent');
                const resolvedTmdbId = (tmdbId && String(tmdbId).trim().length > 0 && String(tmdbId) !== 'null' && String(tmdbId) !== 'undefined') ? String(tmdbId).trim() : null;

                const uploadResult = await MediaModel.create({
                    tmdbId: resolvedTmdbId,
                    mediaType: resolvedMediaType,
                    fileName: targetFile.fileName,
                    fileSize: targetFile.fileSize,
                    fileFormat: targetFile.fileFormat,
                    storagePath: targetFile.fullPath,
                    uploadedBy: req.user.userId,
                    displayTitle: displayTitle ? String(displayTitle).trim() : targetFile.fileName.replace(/\.[^/.]+$/, '')
                });

                const mediaFile = uploadResult.media;
                registeredPathsMap.set(targetFile.fullPath, mediaFile);

                console.log(`[VENUS IMPORT] Película importada desde disco local: ID=${mediaFile.id} | Archivo=${targetFile.fileName} | TMDB=${resolvedTmdbId || 'Independiente'}`);

                results.push({
                    fileId,
                    fileName: targetFile.fileName,
                    success: true,
                    isDuplicate: false,
                    media: mediaFile
                });
            }

            return res.status(200).json({
                message: 'Proceso de importación finalizado',
                results
            });
        } catch (err) {
            console.error('Error al importar archivos locales:', err);
            return res.status(500).json({ error: 'Error interno del servidor durante la importación.' });
        }
    },

    async listMedia(req, res) {
        try {
            const mediaList = await MediaModel.findAll();
            return res.status(200).json({ mediaList });
        } catch (err) {
            console.error('Error al listar contenido multimedia:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async getLibrary(req, res) {
        try {
            const library = await MediaModel.getLibrary();
            return res.status(200).json(library);
        } catch (err) {
            console.error('Error al obtener biblioteca:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async updateTitle(req, res) {
        try {
            const { id } = req.params;
            const { displayTitle } = req.body;
            if (typeof displayTitle !== 'string' || displayTitle.trim().length === 0) {
                return res.status(400).json({ error: 'El título es obligatorio.' });
            }

            const media = await MediaModel.findById(id);
            if (!media) {
                return res.status(404).json({ error: 'Contenido no encontrado.' });
            }

            const updated = await MediaModel.updateTitle(id, displayTitle.trim());
            return res.status(200).json({ message: 'Título actualizado exitosamente', media: updated });
        } catch (err) {
            console.error('Error al actualizar título:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async uploadMedia(req, res) {
        let isCommitted = false;

        req.on('close', () => {
            if (!isCommitted && req.file && req.file.path && fs.existsSync(req.file.path)) {
                try {
                    fs.unlinkSync(req.file.path);
                    console.log(`[VENUS CLEANUP] Conexión cerrada/cancelada. Archivo incompleto eliminado de disco: ${req.file.path}`);
                } catch (e) {}
            }
        });

        try {
            if (!req.file) {
                return res.status(400).json({ error: 'No se ha proporcionado ningún archivo.' });
            }

            const { tmdbId, mediaType, displayTitle } = req.body;
            const resolvedMediaType = mediaType || 'movie';
            const resolvedTmdbId = (tmdbId && tmdbId.toString().trim().length > 0 && tmdbId.toString() !== 'null' && tmdbId.toString() !== 'undefined') ? tmdbId.toString().trim() : null;

            const fileName = req.file.originalname;
            const fileSize = req.file.size;
            const fileFormat = path.extname(fileName).replace('.', '').toLowerCase();
            const storagePath = req.file.path;
            const uploadedBy = req.user.userId;

            const uploadResult = await MediaModel.create({
                tmdbId: resolvedTmdbId,
                mediaType: resolvedMediaType,
                fileName,
                fileSize,
                fileFormat,
                storagePath,
                uploadedBy,
                displayTitle
            });

            isCommitted = true;

            const mediaFile = uploadResult.media;
            const oldMedia = uploadResult.oldMedia;

            if (oldMedia && oldMedia.storage_path && oldMedia.storage_path !== storagePath) {
                try {
                    if (fs.existsSync(oldMedia.storage_path)) {
                        fs.unlinkSync(oldMedia.storage_path);
                        console.log(`[VENUS UPLOAD] Archivo anterior reemplazado y eliminado de disco: ${oldMedia.storage_path}`);
                    }
                } catch (e) {
                    console.error('[VENUS UPLOAD] Error al eliminar archivo anterior:', e);
                }
            }

            console.log(`[VENUS UPLOAD] Archivo subido con éxito: ID=${mediaFile.id} | TMDB=${resolvedTmdbId || 'Independiente'} | Archivo=${fileName} | Ruta=${storagePath}`);

            return res.status(201).json({
                message: 'Archivo subido y asociado exitosamente',
                media: mediaFile
            });
        } catch (err) {
            console.error('Error al subir archivo:', err);
            if (req.file && fs.existsSync(req.file.path)) {
                try {
                    fs.unlinkSync(req.file.path);
                } catch (e) {}
            }
            return res.status(500).json({ error: err.message || 'Error interno del servidor en la subida.' });
        }
    },

    async getMediaInfo(req, res) {
        try {
            const { id } = req.params;
            const media = await MediaModel.findById(id);
            if (!media) {
                return res.status(404).json({ error: 'Contenido no encontrado.' });
            }
            return res.status(200).json({ media });
        } catch (err) {
            console.error('Error al obtener info de medio:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async deleteMedia(req, res) {
        try {
            const { id } = req.params;
            const media = await MediaModel.findById(id);
            if (!media) {
                return res.status(404).json({ error: 'Contenido no encontrado.' });
            }

            if (req.user.role !== 'admin' && media.uploaded_by !== req.user.userId) {
                return res.status(403).json({ error: 'No tienes permisos para eliminar este archivo.' });
            }

            if (fs.existsSync(media.storage_path)) {
                fs.unlinkSync(media.storage_path);
            }

            await MediaModel.delete(id);

            return res.status(200).json({ message: 'Contenido eliminado exitosamente' });
        } catch (err) {
            console.error('Error al eliminar contenido:', err);
            return res.status(500).json({ error: 'Error interno del servidor.' });
        }
    },

    async streamMedia(req, res) {
        try {
            const { id } = req.params; // UUID exacto de media_files.id o tmdbId
            const media = await MediaModel.findById(id) || await MediaModel.findByTmdbId(id);
            if (!media || !fs.existsSync(media.storage_path)) {
                return res.status(404).json({ error: 'Archivo de video no encontrado en el servidor.' });
            }

            console.log(`[VENUS STREAMING] Transmitiendo archivo exacto -> ID: ${media.id} | TMDB: ${media.tmdb_id || 'N/A'} | Archivo: ${media.file_name} | Ruta: ${media.storage_path}`);

            const filePath = media.storage_path;
            const stat = fs.statSync(filePath);
            const fileSize = stat.size;
            const range = req.headers.range;

            const ext = media.file_format.toLowerCase();
            const contentType = ext === 'mkv' ? 'video/x-matroska' :
                                ext === 'webm' ? 'video/webm' :
                                ext === 'avi' ? 'video/x-msvideo' : 'video/mp4';

            if (req.method === 'HEAD') {
                res.writeHead(200, {
                    'Content-Length': fileSize,
                    'Content-Type': contentType,
                    'Accept-Ranges': 'bytes',
                });
                return res.end();
            }

            if (range) {
                const parts = range.replace(/bytes=/, "").split("-");
                const start = parseInt(parts[0], 10);
                const end = parts[1] ? parseInt(parts[1], 10) : fileSize - 1;

                if (start >= fileSize) {
                    res.status(416).setHeader('Content-Range', `bytes */${fileSize}`);
                    return res.end();
                }

                const chunksize = (end - start) + 1;
                const file = fs.createReadStream(filePath, { start, end });
                const head = {
                    'Content-Range': `bytes ${start}-${end}/${fileSize}`,
                    'Accept-Ranges': 'bytes',
                    'Content-Length': chunksize,
                    'Content-Type': contentType,
                };
                res.writeHead(206, head);
                file.pipe(res);
            } else {
                const head = {
                    'Content-Length': fileSize,
                    'Content-Type': contentType,
                    'Accept-Ranges': 'bytes',
                };
                res.writeHead(200, head);
                fs.createReadStream(filePath).pipe(res);
            }
        } catch (err) {
            console.error('Error en stream de video:', err);
            return res.status(500).json({ error: 'Error interno del servidor en streaming.' });
        }
    }
};

Model = MediaModel; // just in case
module.exports = MediaController;
