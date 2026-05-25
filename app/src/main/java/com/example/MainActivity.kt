package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.databinding.ActivityMainBinding
import com.example.databinding.ItemScoreBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var database: ScoreDatabase
    private lateinit var repository: ScoreRepository

    // Game states
    private var currentQuestionIndex = 0
    private var currentScore = 0
    private var playerHearts = 3
    private var quranList: List<Verse> = emptyList()
    private var currentQuizPool: List<Verse> = emptyList()
    
    private var serverVerses: List<Verse> = emptyList()
    private var customVersesList: List<Verse> = emptyList()
    
    private var correctVerse: Verse? = null
    private var selectedSuraName = "Semua Juz 30"
    private var countdownTimer: CountDownTimer? = null
    private val quizLength = 10
    private val questionDurationMs = 20000L // 20 seconds duration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup SharedPreferences
        sharedPrefs = getSharedPreferences("saayat_preferences", Context.MODE_PRIVATE)

        // Setup local Room database persistence
        database = ScoreDatabase.getInstance(this)
        repository = ScoreRepository(database.scoreDao)

        // Setup View Binding for XML Layouts
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load dynamic API URL from SharedPreferences
        val savedServerUrl = sharedPrefs.getString("api_server_url", ApiClient.currentUrl) ?: ApiClient.currentUrl
        ApiClient.updateBaseUrl(savedServerUrl)
        binding.etServerApiUrl.setText(savedServerUrl)

        // Populate local Quran combined dataset first
        updateQuranListCombined()
        
        // Coba ambil ayat dari database server (API) secara latar belakang
        fetchVersesFromServer(isManualCheck = false)

        // Initialize application configurations and listeners
        setupSpinner()
        setupUIListeners()
        loadLocalStats()
        observeLocalScores()
        observeLocalCustomVerses()
    }

    private fun fetchVersesFromServer(isManualCheck: Boolean = false) {
        if (isManualCheck) {
            Toast.makeText(this, "Menghubungkan ke server...", Toast.LENGTH_SHORT).show()
        }
        binding.tvServerStatus.text = "Status: Menghubungkan..."
        binding.tvServerStatus.setTextColor(android.graphics.Color.parseColor("#D97706")) // Amber

        ApiClient.instance.getVerses().enqueue(object : Callback<ApiResponse<List<RemoteVerse>>> {
            override fun onResponse(
                call: Call<ApiResponse<List<RemoteVerse>>>,
                response: Response<ApiResponse<List<RemoteVerse>>>
            ) {
                if (response.isSuccessful && response.body()?.success == true) {
                    val remoteVerses = response.body()?.data
                    if (!remoteVerses.isNullOrEmpty()) {
                        val newVerses = remoteVerses.map {
                            Verse(
                                suraNumber = it.chapter_id,
                                suraName = it.chapter_name,
                                ayahNumber = it.verse_number,
                                textArabic = it.text_arabic
                            )
                        }
                        serverVerses = newVerses
                        updateQuranListCombined()
                        binding.tvServerStatus.text = "Status: Terhubung (${newVerses.size} ayat dari server)"
                        binding.tvServerStatus.setTextColor(android.graphics.Color.parseColor("#15803D")) // Green
                        if (isManualCheck) {
                            Toast.makeText(this@MainActivity, "Berhasil memuat ${newVerses.size} ayat dari database server!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        binding.tvServerStatus.text = "Status: Server kosong, menggunakan database lokal"
                        binding.tvServerStatus.setTextColor(android.graphics.Color.parseColor("#64748B")) // Slate
                        if (isManualCheck) {
                            Toast.makeText(this@MainActivity, "Respons server bermasalah / kosong. Menggunakan database lokal.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    binding.tvServerStatus.text = "Status: Respons server buruk, menggunakan database lokal"
                    binding.tvServerStatus.setTextColor(android.graphics.Color.parseColor("#B91C1C")) // Red
                    if (isManualCheck) {
                        Toast.makeText(this@MainActivity, "Respons server bermasalah. Menggunakan database lokal.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ApiResponse<List<RemoteVerse>>>, t: Throwable) {
                android.util.Log.e("MainActivity", "API Error: ${t.message}", t)
                binding.tvServerStatus.text = "Status: Mode Lokal (Offline)"
                binding.tvServerStatus.setTextColor(android.graphics.Color.parseColor("#64748B")) // Slate
                if (isManualCheck) {
                    Toast.makeText(this@MainActivity, "Koneksi ke API server offline atau IP salah.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun loadLocalStats() {
        val userName = sharedPrefs.getString("profile_name", "Ahmad F.") ?: "Ahmad F."
        val highScore = sharedPrefs.getInt("high_score", 0)
        val playedCount = sharedPrefs.getInt("played_count", 0)
        val streakValue = sharedPrefs.getInt("streak_count", 7)
        val levelValue = sharedPrefs.getInt("user_level", 12)
        
        // Daily target stats (mock starting progress, goes dynamic as they solve correctly)
        val solvedCountToday = sharedPrefs.getInt("solved_count_today", 15)
        val totalTargetToday = 20
        val percentage = ((solvedCountToday.toFloat() / totalTargetToday.toFloat()) * 100).toInt().coerceIn(0, 100)
        
        // Last played surah info
        val lastSurahName = sharedPrefs.getString("last_surah_name", "Al-Baqarah") ?: "Al-Baqarah"
        val lastSurahDetail = sharedPrefs.getString("last_surah_detail", "Ayat 15 • Juz 1") ?: "Ayat 15 • Juz 1"
        
        // Dynamic estimate stats of memorized verses (e.g. 142 + playedCount * 3)
        val totalVersesEstimate = 142 + (playedCount * 3)
        val rankPercentEstimate = if (highScore > 90) "Top 3%" else if (highScore > 60) "Top 8%" else "Top 15%"

        // Bind data to visual elements
        binding.tvWelcomeTitle.text = userName
        binding.etProfileName.setText(userName)
        
        binding.tvHeaderStreak.text = streakValue.toString()
        binding.tvUserLevel.text = "LEVEL $levelValue"
        
        // Target harian widgets
        binding.tvTargetStatus.text = "$solvedCountToday/$totalTargetToday Ayat tersambung"
        binding.circularProgressBar.progress = percentage
        binding.tvProgressPercentage.text = "$percentage%"
        
        // Resume card stats
        binding.tvLastSurahName.text = lastSurahName
        binding.tvLastSurahDetail.text = lastSurahDetail
        
        // Stats grid row
        binding.tvStatTotalVerses.text = totalVersesEstimate.toString()
        binding.tvStatGlobalRank.text = rankPercentEstimate
        
        // Profile sub-panel statistics
        binding.tvProfileHighScore.text = "$highScore pts"
        binding.tvProfilePlayedCount.text = "$playedCount Kali"
    }

    private fun setupSpinner() {
        val uniqueSuras = mutableListOf("Semua Juz 30")
        uniqueSuras.addAll(quranList.map { it.suraName }.distinct())

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, uniqueSuras)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSuraSelection.adapter = adapter
    }

    private fun showPanel(visiblePanel: View) {
        val transition = androidx.transition.Fade()
        transition.duration = 300
        androidx.transition.TransitionManager.beginDelayedTransition(binding.root, transition)

        binding.panelIntro.visibility = if (visiblePanel == binding.panelIntro) View.VISIBLE else View.GONE
        binding.panelMenu.visibility = if (visiblePanel == binding.panelMenu) View.VISIBLE else View.GONE
        binding.panelSelectLevel.visibility = if (visiblePanel == binding.panelSelectLevel) View.VISIBLE else View.GONE
        binding.panelHistory.visibility = if (visiblePanel == binding.panelHistory) View.VISIBLE else View.GONE
        binding.panelUserProfile.visibility = if (visiblePanel == binding.panelUserProfile) View.VISIBLE else View.GONE
        binding.panelQuiz.visibility = if (visiblePanel == binding.panelQuiz) View.VISIBLE else View.GONE

        // Bottom nav visibility control: visible on Home, Level, Peringkat, or Profil
        val showBottomNav = (visiblePanel == binding.panelMenu || 
                             visiblePanel == binding.panelSelectLevel || 
                             visiblePanel == binding.panelHistory || 
                             visiblePanel == binding.panelUserProfile)
                             
        binding.bottomNavigationViewCustom.visibility = if (showBottomNav) View.VISIBLE else View.GONE
        
        if (showBottomNav) {
            updateBottomNavSelection(visiblePanel)
        }
    }

    private fun updateBottomNavSelection(activePanel: View) {
        val activeColor = ContextCompat.getColor(this, R.color.forest_green)
        val inactiveColor = ContextCompat.getColor(this, R.color.gray_text_sub)
        val pillBg = ContextCompat.getDrawable(this, R.drawable.shape_bottom_nav_pill)
        
        // Tab 1: Beranda
        if (activePanel == binding.panelMenu) {
            binding.layoutNavBerandaIconBg.background = pillBg
            binding.tvNavBerandaText.setTextColor(activeColor)
            binding.tvNavBerandaText.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            binding.layoutNavBerandaIconBg.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.tvNavBerandaText.setTextColor(inactiveColor)
            binding.tvNavBerandaText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        
        // Tab 2: Level
        if (activePanel == binding.panelSelectLevel) {
            binding.layoutNavLevelIconBg.background = pillBg
            binding.tvNavLevelText.setTextColor(activeColor)
            binding.tvNavLevelText.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            binding.layoutNavLevelIconBg.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.tvNavLevelText.setTextColor(inactiveColor)
            binding.tvNavLevelText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        
        // Tab 3: Peringkat
        if (activePanel == binding.panelHistory) {
            binding.layoutNavPeringkatIconBg.background = pillBg
            binding.tvNavPeringkatText.setTextColor(activeColor)
            binding.tvNavPeringkatText.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            binding.layoutNavPeringkatIconBg.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.tvNavPeringkatText.setTextColor(inactiveColor)
            binding.tvNavPeringkatText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        
        // Tab 4: Profil
        if (activePanel == binding.panelUserProfile) {
            binding.layoutNavProfilIconBg.background = pillBg
            binding.tvNavProfilText.setTextColor(activeColor)
            binding.tvNavProfilText.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            binding.layoutNavProfilIconBg.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            binding.tvNavProfilText.setTextColor(inactiveColor)
            binding.tvNavProfilText.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun setupUIListeners() {
        // Splash - Intro button
        binding.btnStartApp.setOnClickListener {
            showPanel(binding.panelMenu)
        }

        // Custom Bottom Nav Tab Switchers
        binding.navBeranda.setOnClickListener {
            showPanel(binding.panelMenu)
        }
        binding.navLevel.setOnClickListener {
            showPanel(binding.panelSelectLevel)
        }
        binding.navPeringkat.setOnClickListener {
            showPanel(binding.panelHistory)
        }
        binding.navProfil.setOnClickListener {
            showPanel(binding.panelUserProfile)
        }

        // Save Category / Level
        binding.btnSelectLevelSave.setOnClickListener {
            val selected = binding.spinnerSuraSelection.selectedItem?.toString() ?: "Semua Juz 30"
            selectedSuraName = selected
            
            // Save as last surah
            sharedPrefs.edit()
                .putString("last_surah_name", selected)
                .putString("last_surah_detail", "Terakhir dipilih")
                .apply()
                
            loadLocalStats()
            Toast.makeText(this, "Kategori $selected berhasil dipilih!", Toast.LENGTH_SHORT).show()
            showPanel(binding.panelMenu) // go back to menu
        }

        // Save Profile Name
        binding.btnSaveProfileName.setOnClickListener {
            val typedName = binding.etProfileName.text.toString().trim()
            if (typedName.isNotEmpty()) {
                sharedPrefs.edit().putString("profile_name", typedName).apply()
                loadLocalStats()
                Toast.makeText(this, "Nama berhasil diubah!", Toast.LENGTH_SHORT).show()
                showPanel(binding.panelMenu) // go back to menu
            } else {
                Toast.makeText(this, "Nama tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }

        // Resume Last Surah Card Actions
        binding.cardLastSurah.setOnClickListener {
            val lastS = sharedPrefs.getString("last_surah_name", "Semua Juz 30") ?: "Semua Juz 30"
            selectedSuraName = lastS
            startQuiz()
        }
        binding.btnResumeLast.setOnClickListener {
            val lastS = sharedPrefs.getString("last_surah_name", "Semua Juz 30") ?: "Semua Juz 30"
            selectedSuraName = lastS
            startQuiz()
        }

        // Menu buttons
        binding.btnStartQuiz.setOnClickListener {
            selectedSuraName = sharedPrefs.getString("last_surah_name", "Semua Juz 30") ?: "Semua Juz 30"
            startQuiz()
        }

        binding.cardBtnHistory.setOnClickListener {
            showPanel(binding.panelHistory)
        }

        // Subpage navigation backs
        binding.btnHistoryBack.setOnClickListener {
            showPanel(binding.panelMenu)
        }

        // Simpan & Hubungkan Server API
        binding.btnConnectServer.setOnClickListener {
            val typedUrl = binding.etServerApiUrl.text.toString().trim()
            if (typedUrl.isNotEmpty()) {
                sharedPrefs.edit().putString("api_server_url", typedUrl).apply()
                ApiClient.updateBaseUrl(typedUrl)
                fetchVersesFromServer(isManualCheck = true)
            } else {
                Toast.makeText(this, "URL Server tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }

        // Simpan Ayat Kustom Baru ke Database
        binding.btnSaveCustomVerse.setOnClickListener {
            val rawSuraNum = binding.etCustomSuraNumber.text.toString().trim()
            val suraName = binding.etCustomSuraName.text.toString().trim()
            val rawAyahNum = binding.etCustomAyahNumber.text.toString().trim()
            val ayahText = binding.etCustomAyahText.text.toString().trim()

            if (rawSuraNum.isEmpty() || suraName.isEmpty() || rawAyahNum.isEmpty() || ayahText.isEmpty()) {
                Toast.makeText(this, "Semua kolom input ayat wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val suraNum = rawSuraNum.toIntOrNull()
            val ayahNum = rawAyahNum.toIntOrNull()

            if (suraNum == null || ayahNum == null) {
                Toast.makeText(this, "Nomor surah dan nomor ayat harus berupa angka valid!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val customEntity = CustomVerse(
                    suraNumber = suraNum,
                    suraName = suraName,
                    ayahNumber = ayahNum,
                    textArabic = ayahText
                )
                repository.insertCustomVerse(customEntity)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Berhasil menyimpan ayat kustom: QS. $suraName [$ayahNum]!", Toast.LENGTH_LONG).show()
                    // Clear inputs
                    binding.etCustomSuraNumber.setText("")
                    binding.etCustomSuraName.setText("")
                    binding.etCustomAyahNumber.setText("")
                    binding.etCustomAyahText.setText("")
                }
            }
        }
    }

    private fun startQuiz() {
        // Prepare Quiz Pool of applicable verses (ones with a consecutive next ayah)
        val filtered = if (selectedSuraName == "Semua Juz 30") {
            quranList
        } else {
            quranList.filter { it.suraName == selectedSuraName }
        }

        // Filter and choose candidate questions whose successor is also in the pool
        currentQuizPool = filtered.filter { verse ->
            quranList.any { it.suraNumber == verse.suraNumber && it.ayahNumber == verse.ayahNumber + 1 }
        }

        if (currentQuizPool.isEmpty()) {
            Toast.makeText(this, "Jumlah ayat terpilih tidak memadai untuk game sambung ayat.", Toast.LENGTH_LONG).show()
            return
        }

        // Initialize state variables
        currentQuestionIndex = 0
        currentScore = 0
        playerHearts = 3

        showPanel(binding.panelQuiz)
        loadNextQuestion()
    }

    private fun loadNextQuestion() {
        countdownTimer?.cancel()

        if (playerHearts <= 0) {
            finishGame()
            return
        }

        if (currentQuestionIndex >= quizLength) {
            finishGame()
            return
        }

        currentQuestionIndex++
        binding.tvQuizCounter.text = "Soal $currentQuestionIndex dari $quizLength"
        binding.tvQuizScore.text = "Skor: $currentScore"
        
        // Render heart labels
        val heartIndicators = when(playerHearts) {
            3 -> "❤️❤️❤️"
            2 -> "❤️❤️"
            1 -> "❤️"
            else -> "💀"
        }
        binding.tvQuizHearts.text = heartIndicators

        // Load correct streak count to display on the Flame capsule
        val streakValue = sharedPrefs.getInt("streak_count", 7)
        binding.tvQuizStreak.text = streakValue.toString()

        // Hide feedback controls
        binding.cardQuizFeedback.visibility = View.GONE
        binding.btnNextQuestion.visibility = View.GONE

        // Select a random question verse
        val qVerse = currentQuizPool.random()
        
        // Succession target
        correctVerse = quranList.first { it.suraNumber == qVerse.suraNumber && it.ayahNumber == qVerse.ayahNumber + 1 }

        // Set layout details
        binding.tvQuizArabicVerse.text = "${qVerse.textArabic} ..."
        binding.tvQuizVerseHint.text = "QS. ${qVerse.suraName} : ${qVerse.ayahNumber}"

        // Generate options (1 correct, 3 distractors)
        val candidateDistractors = quranList.filter { it.textArabic != correctVerse?.textArabic && it.textArabic != qVerse.textArabic }
        val shuffledDistractors = candidateDistractors.shuffled().take(3)
        
        val optionsList = mutableListOf<String>()
        optionsList.add(correctVerse!!.textArabic)
        shuffledDistractors.forEach { optionsList.add(it.textArabic) }
        optionsList.shuffle()

        // Bind standard configurations to option views
        val optionViews = listOf(binding.btnOption0, binding.btnOption1, binding.btnOption2, binding.btnOption3)
        val letters = listOf("A", "B", "C", "D")
        for (i in 0..3) {
            val btn = optionViews[i]
            val letterChar = letters[i]
            btn.text = optionsList[i]
            btn.isEnabled = true
            btn.isClickable = true
            
            // Standard light theme look
            btn.strokeColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#E2E8F0"))
            btn.strokeWidth = (1.2f * resources.displayMetrics.density).toInt()
            btn.backgroundTintList = ColorStateList.valueOf(android.graphics.Color.parseColor("#FFFFFF"))
            btn.setTextColor(android.graphics.Color.parseColor("#1E293B"))
            btn.icon = createOptionIcon(letterChar, false, false)

            btn.setOnClickListener {
                checkAnswer(optionsList[i], btn)
            }
            
            // Drop-in Animation for options
            btn.alpha = 0f
            btn.translationY = 35f
            btn.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((i * 100).toLong())
                .setDuration(300)
                .start()
        }
        
        // Animate the verse card entry
        binding.tvQuizArabicVerse.alpha = 0f
        binding.tvQuizArabicVerse.scaleX = 0.92f
        binding.tvQuizArabicVerse.scaleY = 0.92f
        binding.tvQuizArabicVerse.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .start()

        // Cancel and start clock
        binding.progressQuizTime.progress = 100
        startCountdownTimer()

        binding.btnCancelQuiz.setOnClickListener {
            countdownTimer?.cancel()
            showPanel(binding.panelMenu)
        }
    }

    private fun startCountdownTimer() {
        countdownTimer = object : CountDownTimer(questionDurationMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val percentage = ((millisUntilFinished.toFloat() / questionDurationMs) * 100).toInt()
                binding.progressQuizTime.progress = percentage
            }

            override fun onFinish() {
                binding.progressQuizTime.progress = 0
                handleAnswerTimeout()
            }
        }.start()
    }

    private fun checkAnswer(selectedArabic: String, clickedButton: View?) {
        countdownTimer?.cancel()
        disableOptionButtons()

        val isCorrect = selectedArabic == correctVerse?.textArabic
        
        // Animate the bottom sheet feedback sliding up elegantly
        binding.cardQuizFeedback.alpha = 0f
        binding.cardQuizFeedback.translationY = 150f
        binding.cardQuizFeedback.visibility = View.VISIBLE
        binding.cardQuizFeedback.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        val optionViews = listOf(binding.btnOption0, binding.btnOption1, binding.btnOption2, binding.btnOption3)
        val letters = listOf("A", "B", "C", "D")
        val index = optionViews.indexOf(clickedButton)
        val clickedLetter = if (index != -1) letters[index] else "A"

        if (isCorrect) {
            currentScore += 10
            
            // Increment the daily target stats count
            val solvedToday = sharedPrefs.getInt("solved_count_today", 15)
            if (solvedToday < 20) {
                sharedPrefs.edit().putInt("solved_count_today", solvedToday + 1).apply()
            }

            // Update bottom feedback sheet exactly like mock design
            binding.feedbackIconContainer.setCardBackgroundColor(ColorStateList.valueOf(android.graphics.Color.parseColor("#E6F4EA")))
            binding.tvFeedbackIcon.text = "✔"
            binding.tvFeedbackIcon.setTextColor(android.graphics.Color.parseColor("#137333"))
            
            binding.tvFeedbackTitle.text = "Masya Allah!"
            binding.tvFeedbackTitle.setTextColor(android.graphics.Color.parseColor("#0F4C43"))
            binding.tvFeedbackDetails.text = "Jawaban kamu tepat sekali."

            // Green style to correct one
            val mBtn = clickedButton as? com.google.android.material.button.MaterialButton
            mBtn?.strokeColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#0F4C43"))
            mBtn?.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            mBtn?.setTextColor(android.graphics.Color.parseColor("#0F4C43"))
            mBtn?.icon = createOptionIcon(clickedLetter, true, true)
            
            mBtn?.animate()?.scaleX(1.04f)?.scaleY(1.04f)?.setDuration(120)?.withEndAction {
                mBtn.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
            }?.start()
        } else {
            playerHearts--

            // Update bottom feedback sheet for wrong answers
            binding.feedbackIconContainer.setCardBackgroundColor(ColorStateList.valueOf(android.graphics.Color.parseColor("#FCE8E6")))
            binding.tvFeedbackIcon.text = "✕"
            binding.tvFeedbackIcon.setTextColor(android.graphics.Color.parseColor("#C5221F"))
            
            binding.tvFeedbackTitle.text = "Astagfirullah, Salah!"
            binding.tvFeedbackTitle.setTextColor(android.graphics.Color.parseColor("#C5221F"))
            binding.tvFeedbackDetails.text = "Ayat yang benar: ${correctVerse?.textArabic}"

            // Red highlight to clicked mistake
            val mBtn = clickedButton as? com.google.android.material.button.MaterialButton
            mBtn?.strokeColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#DC2626"))
            mBtn?.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            mBtn?.setTextColor(android.graphics.Color.parseColor("#DC2626"))
            mBtn?.icon = createOptionIcon(clickedLetter, true, false)
            
            mBtn?.animate()?.translationX(15f)?.setDuration(50)?.withEndAction {
                mBtn.animate().translationX(-15f).setDuration(50).withEndAction {
                    mBtn.animate().translationX(0f).setDuration(50).start()
                }.start()
            }?.start()

            // Highlight the correct one in green
            highlightCorrectOption()
        }

        binding.tvQuizScore.text = "Skor: $currentScore"
        binding.btnNextQuestion.visibility = View.VISIBLE
        binding.btnNextQuestion.setOnClickListener {
            loadNextQuestion()
        }
    }

    private fun handleAnswerTimeout() {
        disableOptionButtons()
        playerHearts--

        // Transition bottom sheet up
        binding.cardQuizFeedback.alpha = 0f
        binding.cardQuizFeedback.translationY = 150f
        binding.cardQuizFeedback.visibility = View.VISIBLE
        binding.cardQuizFeedback.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        binding.feedbackIconContainer.setCardBackgroundColor(ColorStateList.valueOf(android.graphics.Color.parseColor("#FCE8E6")))
        binding.tvFeedbackIcon.text = "⏳"
        binding.tvFeedbackIcon.setTextColor(android.graphics.Color.parseColor("#C5221F"))
        
        binding.tvFeedbackTitle.text = "Waktu Habis!"
        binding.tvFeedbackTitle.setTextColor(android.graphics.Color.parseColor("#C5221F"))
        binding.tvFeedbackDetails.text = "Ayat kelanjutannya adalah: ${correctVerse?.textArabic}"

        highlightCorrectOption()

        binding.btnNextQuestion.visibility = View.VISIBLE
        binding.btnNextQuestion.setOnClickListener {
            loadNextQuestion()
        }
    }

    private fun highlightCorrectOption() {
        val optionViews = listOf(binding.btnOption0, binding.btnOption1, binding.btnOption2, binding.btnOption3)
        val letters = listOf("A", "B", "C", "D")
        for (i in 0..3) {
            val btn = optionViews[i]
            if (btn.text.toString() == correctVerse?.textArabic) {
                btn.strokeColor = ColorStateList.valueOf(android.graphics.Color.parseColor("#0F4C43"))
                btn.strokeWidth = (2 * resources.displayMetrics.density).toInt()
                btn.setTextColor(android.graphics.Color.parseColor("#0F4C43"))
                btn.icon = createOptionIcon(letters[i], true, true)
            }
        }
    }

    private fun disableOptionButtons() {
        val optionViews = listOf(binding.btnOption0, binding.btnOption1, binding.btnOption2, binding.btnOption3)
        for (btn in optionViews) {
            btn.isEnabled = false
            btn.isClickable = false
        }
    }

    private fun createOptionIcon(letter: String, isSelected: Boolean, isCorrect: Boolean): android.graphics.drawable.Drawable {
        val sizeDp = 30
        val density = resources.displayMetrics.density
        val sizePx = (sizeDp * density).toInt()
        
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val paintCircle = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = if (isSelected) android.graphics.Paint.Style.FILL else android.graphics.Paint.Style.STROKE
            strokeWidth = 1.25f * density
            color = when {
                !isSelected -> android.graphics.Color.parseColor("#94A3B8")
                isCorrect -> android.graphics.Color.parseColor("#0F4C43")
                else -> android.graphics.Color.parseColor("#DC2626")
            }
        }
        
        val radius = sizePx / 2f
        val strokeOffset = paintCircle.strokeWidth / 2f
        val circleRadius = if (isSelected) radius else radius - strokeOffset
        canvas.drawCircle(radius, radius, circleRadius, paintCircle)
        
        val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                !isSelected -> android.graphics.Color.parseColor("#94A3B8")
                else -> android.graphics.Color.parseColor("#FFFFFF")
            }
            textSize = 12.5f * density
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        val fontMetrics = paintText.fontMetrics
        val baselineY = radius - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(letter, radius, baselineY, paintText)
        
        return android.graphics.drawable.BitmapDrawable(resources, bitmap)
    }

    private fun finishGame() {
        countdownTimer?.cancel()
        
        val localHigh = sharedPrefs.getInt("high_score", 0)
        var newBestSet = false
        if (currentScore > localHigh) {
            sharedPrefs.edit().putInt("high_score", currentScore).apply()
            newBestSet = true
        }

        val playedCount = sharedPrefs.getInt("played_count", 0)
        val newPlayedCount = playedCount + 1
        
        // Save level progression dynamically
        val computedLevel = 12 + (newPlayedCount / 3)
        
        // Daily streak logic bonus
        var streakValue = sharedPrefs.getInt("streak_count", 7)
        if (currentScore >= 60) {
            streakValue += 1
        }

        // Save last played surah and session information to display on the Resume card
        sharedPrefs.edit()
            .putInt("played_count", newPlayedCount)
            .putInt("user_level", computedLevel)
            .putInt("streak_count", streakValue)
            .putString("last_surah_name", selectedSuraName)
            .putString("last_surah_detail", "Terakhir latihan: Skor $currentScore")
            .apply()

        // Sync statistics
        loadLocalStats()

        // Insert record into Room local database asynchronously
        val record = ScoreRecord(score = currentScore, surahCompleted = selectedSuraName)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.insert(record)
            withContext(Dispatchers.Main) {
                val congrats = if (newBestSet) {
                    "Maa Shaa Allah! Rekor Baru: $currentScore 🏆"
                } else {
                    "Latihan selesai! Skor Anda: $currentScore"
                }
                Toast.makeText(this@MainActivity, congrats, Toast.LENGTH_LONG).show()
                showPanel(binding.panelHistory)
            }
        }
    }

    // Room persistence reactive observing
    private fun observeLocalScores() {
        lifecycleScope.launch {
            repository.allScores.collect { scoresList ->
                binding.layoutHistoryList.removeAllViews()
                
                if (scoresList.isEmpty()) {
                    binding.tvHistoryEmptyState.visibility = View.VISIBLE
                } else {
                    binding.tvHistoryEmptyState.visibility = View.GONE
                    
                    val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                    for (score in scoresList) {
                        val itemBinding = ItemScoreBinding.inflate(LayoutInflater.from(this@MainActivity), binding.layoutHistoryList, false)
                        
                        itemBinding.tvItemSuraLimit.text = "Surah: ${score.surahCompleted}"
                        itemBinding.tvItemScore.text = "${score.score} Pts"
                        
                        val formattedDate = sdf.format(Date(score.timestamp))
                        itemBinding.tvItemDate.text = formattedDate

                        // Highlight super scores with gold crown
                        if (score.score >= 85) {
                            itemBinding.tvItemBadge.text = "👑"
                        } else {
                            itemBinding.tvItemBadge.text = "🏆"
                        }

                        binding.layoutHistoryList.addView(itemBinding.root)
                    }
                }
            }
        }
    }

    private fun updateQuranListCombined() {
        val combined = mutableListOf<Verse>()
        // 1. Static built-in verses
        combined.addAll(QuranData.verses)
        
        // 2. Server loaded remote verses
        combined.addAll(serverVerses)
        
        // 3. Manually inputted custom verses
        combined.addAll(customVersesList)
        
        quranList = combined.distinctBy { "${it.suraNumber}_${it.ayahNumber}_${it.textArabic}" }
        setupSpinner()
    }

    private fun observeLocalCustomVerses() {
        lifecycleScope.launch {
            repository.allCustomVerses.collect { dbCustomVerses ->
                customVersesList = dbCustomVerses.map {
                    Verse(
                        suraNumber = it.suraNumber,
                        suraName = it.suraName,
                        ayahNumber = it.ayahNumber,
                        textArabic = it.textArabic
                    )
                }
                updateQuranListCombined()
            }
        }
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        super.onDestroy()
    }
}
