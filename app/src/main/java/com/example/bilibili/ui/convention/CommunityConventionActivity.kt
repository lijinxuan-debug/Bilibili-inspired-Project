package com.example.bilibili.ui.convention

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.bilibili.R
import com.example.bilibili.databinding.ActivityCommunityConventionBinding
import java.util.Locale

class CommunityConventionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommunityConventionBinding
    private var currentLocale: ConventionLocale = ConventionLocale.ZH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommunityConventionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupLanguageTabs()
        applyLocale(ConventionLocale.ZH)
    }

    private fun setupLanguageTabs() {
        binding.tabZh.setOnClickListener { applyLocale(ConventionLocale.ZH) }
        binding.tabEn.setOnClickListener { applyLocale(ConventionLocale.EN) }
        binding.tabJa.setOnClickListener { applyLocale(ConventionLocale.JA) }
    }

    private fun applyLocale(locale: ConventionLocale) {
        currentLocale = locale
        updateTabStyle(binding.tabZh, locale == ConventionLocale.ZH)
        updateTabStyle(binding.tabEn, locale == ConventionLocale.EN)
        updateTabStyle(binding.tabJa, locale == ConventionLocale.JA)

        val localized = localizedResources(locale)
        binding.toolbar.title = localized.getString(R.string.convention_screen_title)
        binding.tvDocTitle.text = localized.getString(R.string.convention_doc_title)
        binding.tvUpdateDate.text = localized.getString(R.string.convention_update_date)
        binding.tvEffectiveDate.text = localized.getString(R.string.convention_effective_date)
        binding.tvContent.text = localized.getString(R.string.convention_body)
    }

    private fun updateTabStyle(tab: TextView, selected: Boolean) {
        tab.setBackgroundResource(
            if (selected) R.drawable.bg_convention_tab_selected else R.drawable.bg_convention_tab_normal,
        )
        tab.setTextColor(
            ContextCompat.getColor(
                this,
                if (selected) R.color.bili_pink else R.color.bili_text_grey,
            ),
        )
    }

    private fun localizedResources(locale: ConventionLocale): android.content.res.Resources {
        val config = Configuration(resources.configuration)
        config.setLocale(
            when (locale) {
                ConventionLocale.ZH -> Locale.SIMPLIFIED_CHINESE
                ConventionLocale.EN -> Locale.ENGLISH
                ConventionLocale.JA -> Locale.JAPANESE
            },
        )
        return createConfigurationContext(config).resources
    }

    private enum class ConventionLocale {
        ZH, EN, JA,
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CommunityConventionActivity::class.java))
        }
    }
}
