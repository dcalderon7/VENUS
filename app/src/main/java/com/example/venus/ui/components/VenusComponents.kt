package com.example.venus.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.FrameLayout

object VenusComponents {

    fun dp(context: Context, valor: Int): Int {
        return (valor * context.resources.displayMetrics.density).toInt()
    }

    fun margenAbajo(context: Context, margen: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(context, margen)
        }
    }

    fun fondoRedondeado(color: Int, radio: Int, context: Context): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radio).toFloat()
        }
    }

    fun fondoRedondeadoConRipple(color: Int, radio: Int, context: Context): Drawable {
        val shape = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radio).toFloat()
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(context, radio).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(Color.parseColor("#338738F5")), shape, mask)
    }

    fun aplicarEfectoPulso(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                }
            }
            false
        }
    }

    fun titulo(context: Context, contenedor: LinearLayout, mensaje: String, tamano: Float = 22f) {
        val vista = TextView(context).apply {
            text = mensaje
            textSize = tamano
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        contenedor.addView(vista, margenAbajo(context, 12))
    }

    fun texto(context: Context, contenedor: LinearLayout, mensaje: String, color: Int = Color.rgb(185, 178, 201), tamano: Float = 14f) {
        val vista = TextView(context).apply {
            text = mensaje
            textSize = tamano
            setTextColor(color)
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        }
        contenedor.addView(vista, margenAbajo(context, 12))
    }

    fun etiqueta(context: Context, contenedor: LinearLayout, mensaje: String) {
        val vista = TextView(context).apply {
            text = mensaje.uppercase()
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(196, 181, 253)) // #C4B5FD
            setTypeface(null, Typeface.BOLD)
        }
        contenedor.addView(vista, margenAbajo(context, 8))
    }

    fun boton(context: Context, contenedor: LinearLayout, mensaje: String, accion: () -> Unit, secundario: Boolean = false) {
        val superficie2 = Color.rgb(17, 16, 21)
        val rojo = Color.rgb(135, 56, 245)
        val blanco = Color.WHITE
        val vista = TextView(context).apply {
            text = mensaje
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(blanco)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            background = fondoRedondeado(if (secundario) superficie2 else rojo, 12, context)
            isClickable = true
            isFocusable = true
            setOnClickListener { v ->
                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
                }.start()
                accion()
            }
        }
        aplicarEfectoPulso(vista)
        contenedor.addView(vista, margenAbajo(context, 16))
    }

    fun tarjeta(context: Context, contenedor: LinearLayout, encabezado: String, descripcion: String, accion: (() -> Unit)? = null, alMantener: (() -> Unit)? = null) {
        val superficie = Color.rgb(23, 19, 33)
        val blanco = Color.WHITE
        val gris = Color.rgb(185, 178, 201)

        val caja = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            background = fondoRedondeadoConRipple(superficie, 15, context)
        }

        val nombre = TextView(context).apply {
            text = encabezado
            textSize = 15f
            setTextColor(blanco)
            setTypeface(null, Typeface.BOLD)
            isClickable = false
        }

        val detalle = TextView(context).apply {
            text = descripcion
            textSize = 13f
            setTextColor(gris)
            setLineSpacing(dp(context, 2).toFloat(), 1f)
            isClickable = false
        }

        caja.addView(nombre)
        caja.addView(detalle)

        if (accion != null) {
            caja.isClickable = true
            caja.isFocusable = true
            caja.setOnClickListener { v ->
                v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(70).withEndAction {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
                }.start()
                accion()
            }
            aplicarEfectoPulso(caja)
        }

        if (alMantener != null) {
            caja.isLongClickable = true
            caja.setOnLongClickListener {
                alMantener()
                true
            }
        }

        contenedor.addView(caja, margenAbajo(context, 12))
    }
}
