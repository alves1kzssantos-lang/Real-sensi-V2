package com.realsensi.app

import android.annotation.SuppressLint
import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.random.Random

private const val BG = "#101015"
private const val CARD = "#1A1A22"
private const val ACCENT = "#A66BFF"
private const val TXT = "#FFFFFF"
private const val SUB = "#8A8A99"

class QSwitch(ctx: Context) : Switch(ctx) {
    var quiet = false
    fun set(v: Boolean) {
        quiet = true
        isChecked = v
        quiet = false
    }
}

class RealSensiService : Service() {

    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private lateinit var panel: LinearLayout
    private lateinit var bubble: ImageView
    private lateinit var panelParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var toastCard: LinearLayout
    private lateinit var toastIcon: IconView
    private lateinit var toastTitle: TextView
    private lateinit var toastSub: TextView
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val hideToast = Runnable { toastCard.visibility = View.GONE }
    private var infoRefresh: () -> Unit = {}

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        buildPanel()
        buildBubble()
    }

    override fun onDestroy() {
        running = false
        ui.removeCallbacksAndMessages(null)
        io.shutdownNow()
        if (::panel.isInitialized) wm.removeView(panel)
        if (::bubble.isInitialized) wm.removeView(bubble)
        super.onDestroy()
    }

    private fun togglePanel() {
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    // ================= Mensagem elegante =================

    private fun showToast(title: String, sub: String, ok: Boolean = true) {
        toastTitle.text = title
        toastSub.text = sub
        toastIcon.kind = if (ok) 5 else 6
        toastIcon.forceColor = Color.parseColor(if (ok) "#C9A6FF" else "#FF8A8A")
        toastIcon.invalidate()
        toastCard.background = round(if (ok) "#1F1830" else "#2A1A1F", 14, if (ok) ACCENT else "#FF6B6B")
        toastCard.visibility = View.VISIBLE
        ui.removeCallbacks(hideToast)
        ui.postDelayed(hideToast, 3400)
    }

    // ================= Helpers de UI =================

    private fun round(color: String, r: Int, stroke: String? = null) = GradientDrawable().apply {
        setColor(Color.parseColor(color)); cornerRadius = dp(r).toFloat()
        if (stroke != null) setStroke(dp(1), Color.parseColor(stroke))
    }

    private fun label(text: String, size: Float = 12f, color: String = TXT, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(Color.parseColor(color))
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun btn(text: String, color: String = ACCENT, onClick: () -> Unit) = Button(this).apply {
        this.text = text; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = round(color, 10); stateListAnimator = null
        minHeight = 0; minimumHeight = 0; setPadding(dp(10), dp(8), dp(10), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = round(CARD, 14)
        setPadding(dp(10), dp(8), dp(10), dp(10))
        addView(label(title, 13f, TXT, true))
        addView(View(context).apply {
            setBackgroundColor(Color.parseColor(ACCENT))
            layoutParams = LinearLayout.LayoutParams(dp(60), dp(2)).apply { topMargin = dp(4); bottomMargin = dp(6) }
        })
    }

    private fun twoCols(l: View, r: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(l, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        addView(r, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
    }

    private fun toggleRow(
        parent: LinearLayout, name: String, sub: String? = null,
        checked: Boolean = false, highlight: Boolean = false,
        onChange: (Boolean) -> Boolean
    ): QSwitch {
        val sw = QSwitch(this).apply {
            isChecked = checked
            val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = ColorStateList(st, intArrayOf(Color.parseColor(ACCENT), Color.parseColor("#44445A")))
            thumbTintList = ColorStateList(st, intArrayOf(Color.WHITE, Color.parseColor("#AAAAAA")))
        }
        sw.setOnCheckedChangeListener { _, v ->
            if (sw.quiet) return@setOnCheckedChangeListener
            if (!onChange(v)) sw.set(!v)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(name, 12f, TXT, highlight))
        if (sub != null) col.addView(label(sub, 10f, SUB))
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (highlight) 8 else 0), dp(6), dp(if (highlight) 8 else 0), dp(6))
            if (highlight) background = round("#241B3A", 10)
            addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            addView(sw)
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) })
        return sw
    }

    private fun pills(parent: LinearLayout, title: String, options: List<String>): () -> Int {
        parent.addView(label(title, 11f, SUB))
        var sel = 0
        val views = mutableListOf<TextView>()
        fun paint() = views.forEachIndexed { i, v ->
            v.background = round(if (i == sel) ACCENT else "#2A2A35", 10)
        }
        val row = LinearLayout(this)
        options.forEachIndexed { i, o ->
            val v = label(o, 11f, TXT, true).apply {
                gravity = Gravity.CENTER; setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener { sel = i; paint() }
            }
            views.add(v)
            row.addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(4); topMargin = dp(3); bottomMargin = dp(6)
            })
        }
        paint()
        parent.addView(row)
        return { sel }
    }

    // ================= Bolinha flutuante (logo) =================

    @SuppressLint("ClickableViewAccessibility")
    private fun buildBubble() {
        bubble = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setOval(0, 0, v.width, v.height)
            }
        }
        bubbleParams = WindowManager.LayoutParams(
            dp(52), dp(52), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 200 }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = bubbleParams.x; sy = bubbleParams.y; tx = e.rawX; ty = e.rawY; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - tx; val dy = e.rawY - ty
                    if (abs(dx) > 10 || abs(dy) > 10) moved = true
                    bubbleParams.x = sx + dx.toInt(); bubbleParams.y = sy + dy.toInt()
                    wm.updateViewLayout(bubble, bubbleParams)
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        wm.addView(bubble, bubbleParams)
    }

    // ================= Painel =================

    @SuppressLint("ClickableViewAccessibility")
    private fun buildPanel() {
        val dm = resources.displayMetrics
        val w = minOf(dp(430), dm.widthPixels - dp(16))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(BG, 18, "#2A2A35")
            setPadding(dp(10), dp(8), dp(10), dp(10))
            visibility = View.GONE
        }
        panelParams = WindowManager.LayoutParams(
            w, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (dm.widthPixels - w) / 2; y = dp(6) }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label("REAL SENSI", 16f, ACCENT, true))
            addView(label("Free Fire Tools", 10f, SUB))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) }
        }
        val close = label("X", 14f, TXT, true).apply {
            gravity = Gravity.CENTER; background = round(ACCENT, 10)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { togglePanel() }
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: Outline) = o.setOval(0, 0, v.width, v.height)
            }
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(logo); addView(titleCol); addView(close)
        }
        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        titleCol.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = panelParams.x; sy = panelParams.y; tx = e.rawX; ty = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = sx + (e.rawX - tx).toInt(); panelParams.y = sy + (e.rawY - ty).toInt()
                    wm.updateViewLayout(panel, panelParams)
                }
            }
            true
        }

        val pages = listOf(sensiPage(), realShotPage(), settingsPage(), trainingPage(), infoPage())
        val sideViews = mutableListOf<IconView>()
        fun select(i: Int) {
            pages.forEachIndexed { j, p -> p.visibility = if (i == j) View.VISIBLE else View.GONE }
            sideViews.forEachIndexed { j, v ->
                v.active = i == j
                v.background = if (i == j) round("#3A2A5E", 12, ACCENT) else null
                v.invalidate()
            }
            if (i == 4) infoRefresh()
        }
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = round(CARD, 14)
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }
        val kinds = listOf(0, 1, 2, 3, 4)
        kinds.forEachIndexed { i, k ->
            val v = IconView(this, k).apply { setOnClickListener { select(i) } }
            sideViews.add(v)
            side.addView(v, LinearLayout.LayoutParams(dp(38), dp(38)).apply { bottomMargin = dp(4) })
        }
        val frame = FrameLayout(this)
        pages.forEach { frame.addView(it) }
        val scroll = ScrollView(this).apply { addView(frame); isVerticalScrollBarEnabled = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(side, LinearLayout.LayoutParams(-2, -2))
            addView(scroll, LinearLayout.LayoutParams(0, dp(220), 1f).apply { leftMargin = dp(8) })
        }

        toastIcon = IconView(this, 5)
        toastTitle = label("", 13f, TXT, true)
        toastSub = label("", 11f, "#B9A6DD")
        val toastText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toastTitle); addView(toastSub)
        }
        toastCard = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
            addView(toastIcon, LinearLayout.LayoutParams(dp(30), dp(30)))
            addView(toastText, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(10) })
        }

        select(0)
        panel.addView(header)
        panel.addView(body, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        panel.addView(toastCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        wm.addView(panel, panelParams)
    }

    // ================= Sensi (100 a 200) =================

    private fun sensiPage(): View {
        val left = card("Configurar")
        val style = pills(left, "Estilo", listOf("Rush", "Capa", "Sniper"))
        val level = pills(left, "Celular", listOf("Fraco", "Médio", "Forte"))
        val vary = toggleRow(left, "Variação aleatória", null, true) { true }
        val showDpi = toggleRow(left, "Mostrar DPI", null, true) { true }

        val right = card("Resultado")
        val out = label("Toque em Gerar", 13f, "#C9A6FF")
        right.addView(out)
        right.addView(label("Sugestões. Ajuste no jogo.", 10f, SUB).apply { setPadding(0, dp(8), 0, 0) })

        left.addView(btn("Gerar sensibilidade") {
            out.text = generateSensi(style(), level(), vary.isChecked, showDpi.isChecked)
        })
        right.addView(btn("Copiar", "#2A2A35") {
            val t = out.text.toString()
            if (t.startsWith("Geral")) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("sensi", t))
                showToast("Sensibilidade copiada", "Cole onde quiser.")
            } else {
                showToast("Nada para copiar", "Toque em Gerar sensibilidade primeiro.", false)
            }
        })
        return twoCols(left, right)
    }

    private fun generateSensi(style: Int, level: Int, vary: Boolean, dpiOn: Boolean): String {
        val base = when (style) {
            0 -> intArrayOf(188, 184, 176, 162, 146, 172)
            1 -> intArrayOf(176, 170, 164, 152, 140, 160)
            else -> intArrayOf(160, 156, 156, 148, 144, 150)
        }
        val v = base.map { (it + level * 4 + (if (vary) Random.nextInt(-8, 9) else 0)).coerceIn(100, 200) }
        val dpi = listOf(400, 450, 480)[level] + (if (vary) Random.nextInt(0, 3) * 20 else 0)
        val sb = StringBuilder("Geral: ${v[0]}\nVermelho: ${v[1]}\n2x: ${v[2]}\n4x: ${v[3]}\nAWM: ${v[4]}\nOlhar: ${v[5]}")
        if (dpiOn) sb.append("\nDPI: $dpi")
        return sb.toString()
    }

    // ================= RealShot =================

    private fun realShotPage(): View {
        val left = card("Terminal")
        val term = TextView(this).apply {
            setTextColor(Color.parseColor("#C9A6FF")); typeface = Typeface.MONOSPACE; textSize = 10f
            background = round("#0C0C11", 10); setPadding(dp(8), dp(8), dp(8), dp(8))
            minLines = 9; maxLines = 9; text = "> pronto_"
        }
        left.addView(term)

        val right = card("Controle")
        val clearCache = toggleRow(right, "Limpar cache do painel", null, true) { true }
        val killApps = toggleRow(right, "Encerrar apps em 2º plano", null, true) { true }
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = ColorStateList.valueOf(Color.parseColor(ACCENT))
        }
        right.addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        var busy = false
        right.addView(btn("ATIVAR REALSHOT") {
            if (busy) return@btn
            busy = true; term.text = ""; bar.progress = 0
            val doCache = clearCache.isChecked
            val doKill = killApps.isChecked
            fun log(s: String) { term.append("> $s\n") }
            val steps = listOf("init realshot v2.0", "scan: apps em segundo plano", "scan: arquivos temporários",
                "analisando consumo de memória", "preparando limpeza")
            steps.forEachIndexed { i, s -> ui.postDelayed({ log(s); bar.progress = (i + 1) * 12 }, i * 420L) }
            ui.postDelayed({
                log("carregando...")
                val before = availMem()
                io.execute {
                    val freed = if (doCache) clearOwnCache() else 0L
                    val killed = if (doKill) killBackground() else 0
                    ui.postDelayed({
                        val after = availMem()
                        bar.progress = 100
                        if (doCache) log("cache liberado: ${freed / 1048576} MB")
                        if (doKill) log("apps encerrados: $killed")
                        log("RAM livre: ${before / 1048576} -> ${after / 1048576} MB")
                        log("concluido")
                        showToast("RealShot concluído", "$killed apps encerrados. RAM livre: ${after / 1048576} MB.")
                        busy = false
                    }, 900)
                }
            }, steps.size * 420L + 300L)
        })
        right.addView(btn("Limpeza do sistema", "#2A2A35") {
            try {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                showToast("Não consegui abrir", "Abra Armazenamento nas configurações.", false)
            }
        })
        right.addView(label("O Android só permite limpar o cache de outros apps pela tela do sistema.", 9f, SUB).apply {
            setPadding(0, dp(6), 0, 0)
        })
        return twoCols(left, right)
    }

    private fun availMem(): Long {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem
    }

    private fun clearOwnCache(): Long {
        var total = 0L
        val dirs = listOfNotNull(cacheDir, codeCacheDir, externalCacheDir)
        for (d in dirs) {
            total += d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            d.listFiles()?.forEach { it.deleteRecursively() }
        }
        return total
    }

    private fun killBackground(): Int {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val keep = setOf(packageName, "com.dts.freefireth", "com.dts.freefiremax")
        val pkgs = packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }.distinct().filter { it !in keep }
        pkgs.forEach { am.killBackgroundProcesses(it) }
        return pkgs.size
    }

    // ================= 
