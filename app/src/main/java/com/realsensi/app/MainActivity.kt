package com.realsensi.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.math.cos
import kotlin.math.sin

private const val BG = "#101015"
private const val CARD = "#1A1A22"
private const val ACCENT = "#A66BFF"
private const val SUB = "#8A8A99"

/** Ícones desenhados: 0 alvo, 1 raio, 2 ajustes, 3 halter, 4 engrenagem, 5 check, 6 alerta */
class IconView(ctx: Context, var kind: Int) : View(ctx) {
    var active = false
    var forceColor: Int? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(c: Canvas) {
        p.color = forceColor ?: Color.parseColor(if (active) ACCENT else SUB)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.28f
        p.style = Paint.Style.STROKE
        when (kind) {
            0 -> {
                c.drawCircle(cx, cy, r, p)
                c.drawCircle(cx, cy, r * 0.5f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, r * 0.14f, p)
            }
            1 -> {
                p.style = Paint.Style.FILL
                val path = Path().apply {
                    moveTo(cx + r * 0.15f, cy - r); lineTo(cx - r * 0.55f, cy + r * 0.1f)
                    lineTo(cx - r * 0.05f, cy + r * 0.1f); lineTo(cx - r * 0.15f, cy + r)
                    lineTo(cx + r * 0.55f, cy - r * 0.15f); lineTo(cx + r * 0.05f, cy - r * 0.15f)
                    close()
                }
                c.drawPath(path, p)
            }
            2 -> {
                for (i in -1..1) {
                    val y = cy + i * r * 0.7f
                    c.drawLine(cx - r, y, cx + r, y, p)
                }
                p.style = Paint.Style.FILL
                c.drawCircle(cx - r * 0.4f, cy - r * 0.7f, r * 0.22f, p)
                c.drawCircle(cx + r * 0.4f, cy, r * 0.22f, p)
                c.drawCircle(cx - r * 0.1f, cy + r * 0.7f, r * 0.22f, p)
            }
            3 -> {
                c.drawLine(cx - r * 0.6f, cy, cx + r * 0.6f, cy, p)
                c.drawLine(cx - r * 0.6f, cy - r * 0.55f, cx - r * 0.6f, cy + r * 0.55f, p)
                c.drawLine(cx + r * 0.6f, cy - r * 0.55f, cx + r * 0.6f, cy + r * 0.55f, p)
                c.drawLine(cx - r, cy - r * 0.28f, cx - r, cy + r * 0.28f, p)
                c.drawLine(cx + r, cy - r * 0.28f, cx + r, cy + r * 0.28f, p)
            }
            4 -> {
                c.drawCircle(cx, cy, r * 0.45f, p)
                for (k in 0 until 8) {
                    val a = k * Math.PI / 4
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    c.drawLine(cx + r * 0.75f * ca, cy + r * 0.75f * sa, cx + r * 1.05f * ca, cy + r * 1.05f * sa, p)
                }
            }
            5 -> {
                val path = Path().apply {
                    moveTo(cx - r * 0.7f, cy); lineTo(cx - r * 0.2f, cy + r * 0.5f); lineTo(cx + r * 0.75f, cy - r * 0.5f)
                }
                c.drawPath(path, p)
            }
            else -> {
                c.drawLine(cx, cy - r, cx, cy + r * 0.2f, p)
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy + r * 0.75f, r * 0.12f, p)
            }
        }
    }
}

class MainActivity : Activity() {

    private val KEY_HASH = "56142019170dbfe8599a3793bee77d55e5c5a8b08a3517be17f0296098300f93"
    private lateinit var root: LinearLayout
    private var loggedIn = false

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = Color.parseColor(BG)
        window.navigationBarColor = Color.parseColor(BG)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        val saved = prefs().getString("key", null)
        loggedIn = prefs().getBoolean("remember", true) && saved != null && sha256(saved.lowercase()) == KEY_HASH
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor(BG))
            isFillViewport = true
            addView(root)
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        if (loggedIn || root.childCount == 0) render()
    }

    private fun render() {
        root.removeAllViews()

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.logo)
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(60))
        })
        val names = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(14) }
        }
        names.addView(txt("Real Sensi", 22f, "#FFFFFF", true))
        names.addView(txt("Free Fire Tools", 13f, ACCENT, false))
        header.addView(names)
        root.addView(header)

        root.addView(if (loggedIn) activeCard() else loginCard())

        root.addView(txt("O que o painel libera", 12f, SUB, false).apply {
            setPadding(dp(2), dp(22), 0, dp(8))
        })
        root.addView(tilesRow(0, "Sensi", "100 a 200", 1, "RealShot", "Limpeza"))
        root.addView(tilesRow(2, "Ajustes", "120 Hz e mais", 3, "Treino", "Rotinas"))
        root.addView(txt("Real Sensi v1.0", 11f, "#55556A", false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        })
    }

    private fun loginCard(): View {
        val card = cardBox()
        card.addView(txt("Entrar com sua Key", 15f, "#FFFFFF", true))
        card.addView(View(this).apply {
            setBackgroundColor(Color.parseColor(ACCENT))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(2)).apply { topMargin = dp(5); bottomMargin = dp(12) }
        })

        val field = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = round(BG, 12, ACCENT)
            setPadding(dp(14), 0, dp(8), 0)
        }
        val input = EditText(this).apply {
            hint = "Sua Key"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 15f
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            layoutParams = LinearLayout.LayoutParams(0, dp(50), 1f)
        }
        var visible = false
        val eye = txt("Ver", 12f, ACCENT, true).apply { setPadding(dp(10), dp(8), dp(10), dp(8)) }
        eye.setOnClickListener {
            visible = !visible
            input.inputType = InputType.TYPE_CLASS_TEXT or
                (if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
            input.setSelection(input.text.length)
            eye.text = if (visible) "Ocultar" else "Ver"
        }
        val paste = txt("Colar", 12f, ACCENT, true).apply { setPadding(dp(10), dp(8), dp(6), dp(8)) }
        paste.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (!t.isNullOrEmpty()) {
                input.setText(t)
                input.setSelection(input.text.length)
            }
        }
        field.addView(input); field.addView(eye); field.addView(paste)
        card.addView(field)

        val remember = Switch(this).apply {
            isChecked = prefs().getBoolean("remember", true)
            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(st, intArrayOf(Color.parseColor(ACCENT), Color.parseColor("#44445A")))
            thumbTintList = ColorStateList(st, intArrayOf(Color.WHITE, Color.parseColor("#AAAAAA")))
        }
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        row.addView(txt("Lembrar Key", 13f, "#FFFFFF", false).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        row.addView(remember)
        card.addView(row)

        val msg = txt("", 12f, "#FF6B6B", false).apply { setPadding(0, dp(10), 0, 0) }
        card.addView(btn("Entrar") {
            val k = input.text.toString().trim().lowercase()
            if (sha256(k) == KEY_HASH) {
                val e = prefs().edit().putBoolean("remember", remember.isChecked)
                if (remember.isChecked) e.putString("key", k) else e.remove("key")
                e.apply()
                loggedIn = true
                render()
            } else {
                msg.text = "Key inválida. Confira e tente de novo."
            }
        })
        card.addView(msg)
        return card
    }

    private fun activeCard(): View {
        val card = cardBox()
        val running = RealSensiService.running
        card.addView(txt("Key ativada", 16f, "#6BFF95", true))
        card.addView(txt("Sua licença está liberada neste aparelho.", 12f, SUB, false).apply {
            setPadding(0, dp(4), 0, 0)
        })
        card.addView(btn(if (running) "Desativar painel flutuante" else "Ativar painel flutuante") {
            if (running) {
                stopService(Intent(this, RealSensiService::class.java))
                root.postDelayed({ render() }, 400)
            } else if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Ative 'Exibir sobre outros apps' e volte", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                startForegroundService(Intent(this, RealSensiService::class.java))
                root.postDelayed({ render() }, 400)
            }
        })
        card.addView(txt("Depois de ativar, toque na bolinha com o logo para abrir o painel.", 11f, SUB, false).apply {
            setPadding(0, dp(10), 0, 0)
        })
        card.addView(btn("Sair da conta", "#2A2A35") {
            prefs().edit().remove("key").apply()
            stopService(Intent(this, RealSensiService::class.java))
            loggedIn = false
            render()
        })
        return card
    }

    private fun tilesRow(k1: Int, t1: String, s1: String, k2: Int, t2: String, s2: String): View {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }
        row.addView(tile(k1, t1, s1), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        row.addView(tile(k2, t2, s2), LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        return row
    }

    private fun tile(kind: Int, title: String, sub: String): View {
        val t = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = round(CARD, 14, "#2A2A35")
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        t.addView(IconView(this, kind).apply {
            active = true
            background = round("#241B3A", 10)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(10) }
        }
        col.addView(txt(title, 13f, "#FFFFFF", true))
        col.addView(txt(sub, 10f, SUB, false))
        t.addView(col)
        return t
    }

    private fun cardBox() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(CARD, 18, "#2A2A35")
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) }
    }

    private fun txt(t: String, size: Float, color: String, bold: Boolean) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.parseColor(color))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun btn(t: String, color: String = ACCENT, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        background = round(color, 12); stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(14) }
        setOnClickListener { onClick() }
    }

    private fun round(color: String, r: Int, stroke: String? = null) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
    }

    private fun sha256(t: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(t.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun prefs() = getSharedPreferences("realsensi", Context.MODE_PRIVATE)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
