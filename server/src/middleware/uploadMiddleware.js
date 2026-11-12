const multer = require('multer');
const path = require('path');
const crypto = require('crypto');
const fs = require('fs');

const uploadDir = path.join(__dirname, '../../uploads');
if (!fs.existsSync(uploadDir)) {
    fs.mkdirSync(uploadDir, { recursive: true });
}

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, uploadDir);
    },
    filename: (req, file, cb) => {
        const uniqueSuffix = crypto.randomBytes(16).toString('hex');
        const ext = path.extname(file.originalname).toLowerCase();
        cb(null, `venus_${Date.now()}_${uniqueSuffix}${ext}`);
    }
});

const fileFilter = (req, file, cb) => {
    const allowedTypes = ['.mp4', '.mkv', '.avi', '.mov', '.webm', '.m4v'];
    const ext = path.extname(file.originalname).toLowerCase();
    if (allowedTypes.includes(ext)) {
        cb(null, true);
    } else {
        cb(new Error('Formato de archivo no permitido. Solo se aceptan videos (MP4, MKV, AVI, etc.).'), false);
    }
};

const upload = multer({
    storage: storage,
    fileFilter: fileFilter,
    limits: {
        fileSize: 5 * 1024 * 1024 * 1024 // 5 GB limit
    }
});

module.exports = upload;
