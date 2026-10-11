package com.realsensi.app

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.text.InputType
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

private const val BG = "#000000"
private const val CARD = "#111111"
private const val ACCENT = "#FF7A00"

class RealSensiService : Service() {

    companion object { var onPhotoChanged: (() -> Unit)? = null }

    private lateinit var wm: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var params: WindowManager.LayoutParams
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = round(BG, 18, ACCENT)
        }
        params = WindowManager.LayoutParams(
            dp(320), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 140 }
        wm.addView(root, params)
        val saved = prefs().getString("key", null)
        if (saved != null) validate(saved, true) else showKeyScreen()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        onPhotoChanged = null
        io.shutdownNow()
        if (::root.isInitialized) wm.removeView(root)
        super.onDestroy()
    }

    private fun round(color: String, r: Int, stroke: String? = null) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
    }

    private fun btn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text; setTextColor(Color.BLACK); isAllCaps = false
        background = round(ACCENT, 12); stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun label(text: String, size: Float = 13f, color: String = "#FFFFFF") =
        TextView(this).apply { this.text = text; textSize = size; setTextColor(Color.parseColor(color)) }

    private fun allowKeyboard(v: EditText) = v.setOnFocusChangeListener { _, has ->
        params.flags = if (has) params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        wm.updateViewLayout(root, params)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun titleBar(body: View): View {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = label("⚡ REAL SENSI", 16f, ACCENT).apply {
            typeface = Typeface.DEFAULT_BOLD; layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            setPadding(0, dp(4), 0, dp(4))
        }
        bar.addView(title)
        bar.addView(label("—", 18f).apply {
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { body.visibility = if (body.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        })
        bar.addView(label("✕", 16f).apply { setOnClickListener { stopSelf() } })
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        title.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (e.rawX - tx).toInt(); params.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(root, params)
                }
            }; true
        }
        return bar
    }

    private fun showKeyScreen(error: String? = null) {
        root.removeAllViews()
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val input = EditText(this).apply {
            hint = "Digite sua Key"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_TEXT; background = round(CARD, 12, "#333333")
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        allowKeyboard(input)
        val msg = label(error ?: "", 12f, "#FF6B6B")
        body.addView(input)
        body.addView(btn("Ativar") {
            val k = input.text.toString().trim()
            if (k.isEmpty()) { msg.text = "Digite a Key."; return@btn }
            msg.setTextColor(Color.LTGRAY); msg.text = "Verificando…"; validate(k, false)
        })
        body.addView(msg)
        root.addView(titleBar(body)); root.addView(body)
    }

    private fun showMainPanel() {
        root.removeAllViews()
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(CARD, 14)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f); setPadding(dp(8), 0, 0, 0)
        }
        val pages = listOf(
            "👤" to profilePage(), "🎯" to sensiPage(), "⚡" to realShotPage(), "🏋️" to trainingPage()
        )
        pages.forEach { content.addView(it.second) }
        fun select(i: Int) = pages.forEachIndexed { j, p ->
            p.second.visibility = if (i == j) View.VISIBLE else View.GONE
        }
        pages.forEachIndexed { i, p ->
            side.addView(label(p.first, 22f).apply {
                gravity = Gravity.CENTER; setPadding(dp(10), dp(10), dp(10), dp(10))
                setOnClickListener { select(i) }
            })
        }
        select(0)
        body.addView(side); body.addView(content)
        root.addView(titleBar(body)); root.addView(body)
    }

    private fun profilePage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val photo = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(84))
            scaleType = ImageView.ScaleType.CENTER_CROP; background = round(CARD, 42, ACCENT)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setOval(0, 0, v.width, v.height)
            }
        }
        fun loadPhoto() {
            val f = File(filesDir, "avatar.jpg")
            if (f.exists()) photo.setImageBitmap(BitmapFactory.decodeFile(f.path))
        }
        loadPhoto()
        onPhotoChanged = { ui.post { loadPhoto() } }
        photo.setOnClickListener {
            startActivity(Intent(this, PhotoPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val name = EditText(this).apply {
            hint = "Seu nome"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; background = round(CARD, 12, "#333333")
            setText(prefs().getString("name", ""))
        }
        allowKeyboard(name)
        page.addView(photo); page.addView(label("Toque na foto para trocar", 10f, "#888888"))
        page.addView(name)
        page.addView(btn("Salvar nome") {
            prefs().edit().putString("name", name.text.toString().trim()).apply()
            Toast.makeText(this, "Salvo!", Toast.LENGTH_SHORT).show()
        })
        page.addView(btn("Sair da conta") { prefs().edit().remove("key").apply(); showKeyScreen() }.apply {
            background = round("#333333", 12); setTextColor(Color.WHITE)
        })
        return page
    }

    private fun sensiPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val style = chips(page, "Estilo", listOf("Rush", "Capa", "Sniper"))
        val level = chips(page, "Celular", listOf("Fraco", "Médio", "Forte"))
        val out = label("", 13f, "#6BFF95")
        page.addView(btn("Gerar sensibilidade") { out.text = generateSensi(style(), level()) })
        page.addView(out)
        page.addView(label("Sugestões. Ajuste no jogo.", 10f, "#888888"))
        return page
    }

    private fun chips(parent: LinearLayout, title: String, options: List<String>): () -> Int {
        parent.addView(label(title, 12f, "#AAAAAA"))
        val g = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        options.forEachIndexed { i, o ->
            g.addView(RadioButton(this).apply {
                id = 200 + i; text = o; textSize = 12f; setTextColor(Color.LTGRAY); isChecked = i == 0
                buttonTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT))
            })
        }
        parent.addView(g)
        return { g.checkedRadioButtonId - 200 }
    }

    private fun generateSensi(style: Int, level: Int): String {
        val base = when (style) {
            0 -> intArrayOf(95, 92, 88, 80, 70, 85)
            1 -> intArrayOf(88, 85, 82, 76, 68, 80)
            else -> intArrayOf(80, 78, 78, 74, 72, 75)
        }
        val v = base.map { (it + level * 2 + Random.nextInt(-4, 5)).coerceIn(0, 100) }
        val dpi = listOf(400, 450, 480)[level] + Random.nextInt(0, 3) * 20
        return "Geral: ${v[0]}\nVermelho: ${v[1]}\n2x: ${v[2]}\n4x: ${v[3]}\nAWM: ${v[4]}\nOlhar: ${v[5]}\nDPI: $dpi"
    }

    private fun realShotPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val term = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF66")); typeface = Typeface.MONOSPACE; textSize = 10f
            background = round("#050505", 12, "#1F3D2B"); setPadding(dp(8), dp(8), dp(8), dp(8))
            minLines = 8; maxLines = 8; text = "> pronto_"
        }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(ACCENT))
        }
        var running = false
        val go = btn("ATIVAR REALSHOT") {}
        go.setOnClickListener {
            if (running) return@setOnClickListener
            running = true; term.text = ""; bar.progress = 0
            fun log(s: String) { term.append("> $s\n") }
            val intro = listOf("init realshot v2.0", "scan: apps em segundo plano", "scan: arquivos temporários",
                "analisando consumo de bateria", "preparando limpeza")
            intro.forEachIndexed { i, s -> ui.postDelayed({ log(s); bar.progress = (i + 1) * 10 }, i * 450L) }
            ui.postDelayed({
                log("carregando…")
                io.execute {
                    val killed = cleanBackground()
                    ui.post {
                        bar.progress = 100
                        log("cache do painel limpo")
                        log("apps encerrados: $killed")
                        log("concluído ✔")
                        Toast.makeText(this, "RealShot: $killed apps encerrados", Toast.LENGTH_LONG).show()
                        running = false
                    }
                }
            }, intro.size * 450L + 300L)
        }
        page.addView(term); page.addView(bar); page.addView(go)
        page.addView(label("Encerra apps em segundo plano permitidos pelo Android e limpa o cache do painel.", 9f, "#888888"))
        return page
    }

    private fun cleanBackground(): Int {
        cacheDir.deleteRecursively()
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val keep = setOf(packageName, "com.dts.freefireth", "com.dts.freefiremax")
        val pkgs = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }.distinct().filter { it !in keep }
        pkgs.forEach { am.killBackgroundProcesses(it) }
        return pkgs.size
    }

    private fun trainingPage(): View {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val players = listOf("Nobru", "Bak", "Cerol")
        val pick = chips(page, "Estilo inspirado em", players)
        val out = label("", 12f, "#DDDDDD")
        page.addView(btn("Gerar rotina") { out.text = routine(players[pick()]) })
        page.addView(ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dp(150)); addView(out)
        })
        return page
    }

    private fun routine(player: String): String {
        val focus = when (player) {
            "Nobru" -> listOf("Rush e entrada rápida", "Reflexo em X1", "Controle de spray curto")
            "Bak" -> listOf("Leitura de zona e posição", "Tomada de decisão", "Mira em média distância")
            else -> listOf("Agressividade controlada", "Troca rápida de armas", "Rotação e comunicação")
        }
        val w = Random.nextInt(8, 13)
        return """
            Rotina (estilo $player) ~${w + 45} min
            1. Aquecimento dos dedos: $w min
            2. ${focus[0]}: 15 min
            3. ${focus[1]}: 15 min
            4. ${focus[2]}: 15 min
            5. Revisar 1 partida: 5 min
            Dica: pausa de 5 min a cada hora.
        """.trimIndent()
    }

    private val KEY_HASH = "56142019170dbfe8599a3793bee77d55e5c5a8b08a3517be17f0296098300f93"

    private fun sha256(t: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest(t.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun validate(key: String, silent: Boolean) {
        if (sha256(key.trim().lowercase()) == KEY_HASH) {
            prefs().edit().putString("key", key).apply(); showMainPanel()
        } else showKeyScreen(if (silent) null else "Key inválida.")
    }

    private fun prefs() = getSharedPreferences("realsensi", Context.MODE_PRIVATE)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun startAsForeground() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(NotificationChannel("rs", "Real Sensi", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, Notification.Builder(this, "rs").setContentTitle("Real Sensi ativo")
            .setSmallIcon(android.R.drawable.ic_menu_manage).build())
    }
}

class PhotoPickerActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), 1)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        val uri: Uri? = data?.data
        if (res == RESULT_OK && uri != null) {
            contentResolver.openInputStream(uri)?.use { s ->
                val bmp = BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                if (bmp != null) {
                    val sq = Bitmap.createScaledBitmap(bmp, 256, 256, true)
                    File(filesDir, "avatar.jpg").outputStream().use { sq.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    RealSensiService.onPhotoChanged?.invoke()
                }
            }
        }
        finish()
    }
}
