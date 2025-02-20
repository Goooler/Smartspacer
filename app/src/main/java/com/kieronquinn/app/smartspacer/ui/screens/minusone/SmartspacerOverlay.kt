package com.kieronquinn.app.smartspacer.ui.screens.minusone

import android.app.LocalActivityManagerCompat
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.lifecycleScope
import com.kieronquinn.app.smartspacer.components.blur.BlurProvider
import com.kieronquinn.app.smartspacer.databinding.OverlaySmartspacerBinding
import com.kieronquinn.app.smartspacer.repositories.ExpandedRepository
import com.kieronquinn.app.smartspacer.repositories.SmartspacerSettingsRepository
import com.kieronquinn.app.smartspacer.ui.activities.ExpandedActivity
import com.kieronquinn.app.smartspacer.ui.screens.base.BaseOverlay
import com.kieronquinn.app.smartspacer.utils.extensions.removeStatusNavBackgroundOnPreDraw
import com.kieronquinn.app.smartspacer.utils.extensions.whenResumed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.koin.core.component.inject
import kotlin.math.roundToInt

class SmartspacerOverlay(context: Context): BaseOverlay<OverlaySmartspacerBinding>(context, OverlaySmartspacerBinding::inflate) {

    companion object {
        private const val KEY_ACTIVITY_STATE = "activity_state"
        private const val KEY_BACKGROUND_BLUR_PROGRESS = "background_blur_progress"
    }

    private val activityManager = LocalActivityManagerCompat(context, true)
    private var backgroundBlurProgress = 0f
    private val blurProvider by inject<BlurProvider>()
    private val expandedRepository by inject<ExpandedRepository>()
    private val settingsRepository by inject<SmartspacerSettingsRepository>()
    private var isResumed = false

    private val blurBackground = settingsRepository.expandedBlurBackground.asFlow()
        .stateIn(
            lifecycleScope,
            SharingStarted.Eagerly,
            settingsRepository.expandedBlurBackground.getSync()
        )

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        activityManager.dispatchCreate(bundle?.getBundle(KEY_ACTIVITY_STATE) ?: Bundle())
        setupBackPress()
        window?.decorView?.removeStatusNavBackgroundOnPreDraw()
        val window = activityManager.startActivity(
            "overlay", ExpandedActivity.createOverlayIntent(this)
        )
        backgroundBlurProgress = bundle
            ?.getFloat(KEY_BACKGROUND_BLUR_PROGRESS, 0f) ?: 0f
        binding.root.addView(window.decorView.removeStatusNavBackgroundOnPreDraw())
    }

    override fun onPause() {
        if(!isResumed) return
        isResumed = false
        super.onPause()
        activityManager.dispatchPause(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        activityManager.dispatchDestroy(true)
    }

    override fun onResume() {
        if(isResumed) return
        isResumed = true
        super.onResume()
        activityManager.dispatchResume()
        updateProgressViews(backgroundBlurProgress, true)
    }

    override fun onDragProgress(progress: Float) {
        super.onDragProgress(progress)
        updateProgressViews(progress)
        whenResumed {
            expandedRepository.onOverlayDragProgressChanged()
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putBundle(KEY_ACTIVITY_STATE, activityManager.saveInstanceState())
        bundle.putFloat(KEY_BACKGROUND_BLUR_PROGRESS, backgroundBlurProgress)
    }

    private fun updateProgressViews(progress: Float, force: Boolean = false){
        if(backgroundBlurProgress == progress && !force) return
        backgroundBlurProgress = progress
        if(blurBackground.value) {
            binding.root.background = null
            blurProvider.applyBlurToWindow(window!!, progress)
        }else{
            val backgroundColour = ColorUtils.setAlphaComponent(
                Color.BLACK, (127.5 * progress).roundToInt()
            )
            binding.root.background = ColorDrawable(backgroundColour)
            blurProvider.applyBlurToWindow(window!!, 0f)
        }
    }

    private fun setupBackPress() = whenResumed {
        expandedRepository.overlayBackPressedBus.collect {
            closePanel()
        }
    }

}