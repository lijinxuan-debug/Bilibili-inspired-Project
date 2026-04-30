package com.example.bilibili

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.bilibili.databinding.ActivityMainBinding
import com.example.bilibili.ui.focus.FocusOnFragment
import com.example.bilibili.ui.front.FrontPageFragment
import com.example.bilibili.ui.memberShip.MemberShipFragment
import com.example.bilibili.ui.personal.PersonalFragment
import com.example.bilibili.ui.releaseVideo.ReleaseVideoActivity
import com.example.bilibili.util.GlideEngine
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.config.SelectModeConfig
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnResultCallbackListener

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    // Fragment标签常量
    companion object {
        const val TAG_HOME = "tag_home"
        const val TAG_FOCUS = "tag_focus"
        const val TAG_SHOP = "tag_shop"
        const val TAG_MINE = "tag_mine"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 默认显示首页
        if (savedInstanceState == null) {
            switchFragment(TAG_HOME)
        }

        // 设置底部导航栏监听
        binding.bottomNavView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchFragment(TAG_HOME)
                R.id.nav_focus_on -> switchFragment(TAG_FOCUS)
                R.id.nav_shop -> switchFragment(TAG_SHOP)
                R.id.nav_mine -> switchFragment(TAG_MINE)
            }
            true
        }

        // 监听添加按钮
        binding.fabAdd.setOnClickListener {
            // 检查对应的视频权限依赖（包括相机权限）
            XXPermissions.with(this)
                .permission(Permission.READ_MEDIA_VIDEO)
                .permission(Permission.READ_MEDIA_IMAGES)
                .permission(Permission.CAMERA) // 添加相机权限
                .request(object : OnPermissionCallback {
                    override fun onGranted(permissions: MutableList<String>, allGranted: Boolean) {
                        if (allGranted) {
                            // 权限通过，进入第二步：打开相册
                            startVideoPicker()
                        }
                    }

                    override fun onDenied(
                        permissions: MutableList<String>,
                        doNotAskAgain: Boolean
                    ) {
                        // 权限被拒的处理逻辑
                        if (doNotAskAgain) {
                            // 用户勾选了"不再询问"，引导用户去设置页面
                            XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                        } else {
                            // 权限被拒绝，提示用户
                            android.widget.Toast.makeText(
                                this@MainActivity,
                                "需要相机和媒体权限才能发布视频",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                })
        }
    }

    private fun startVideoPicker() {
        PictureSelector.create(this)
            .openGallery(SelectMimeType.ofVideo()) // 只看视频
            .setImageEngine(GlideEngine)           // 注入加载引擎
            .setSelectionMode(SelectModeConfig.SINGLE) // 强制单选
            .isDisplayCamera(true)                 // 允许直接录制
            .setMaxSelectNum(1)                    // 最多选1个
            .isPreviewVideo(false)                 // 禁用视频预览功能
            .isPreviewImage(false)                 // 禁用图片预览功能
            .isPreviewAudio(false)                 // 禁用音频预览功能
            .forResult(object : OnResultCallbackListener<LocalMedia> {
                override fun onResult(result: ArrayList<LocalMedia>?) {
                    val media = result?.getOrNull(0)
                    if (media != null) {
                        // 1. 拿到绝对路径
                        val videoPath = media.realPath
                        // 2. 拿到视频时长（毫秒）
                        val duration = media.duration

                        Log.d("BiliSelect", "路径: $videoPath, 时长: ${duration / 1000}秒")

                        // 直接跳转到发布视频页面，让用户在页面中添加分P
                        val intent =
                            Intent(this@MainActivity, ReleaseVideoActivity::class.java).apply {
                                putExtra("video_path", videoPath)
                                putExtra("video_duration", duration)
                            }
                        startActivity(intent)
                    }
                }

                override fun onCancel() {
                    Log.d("BiliSelect", "用户溜了，啥也没选")
                }
            })
    }

    private fun switchFragment(tag: String) {
        val transaction = supportFragmentManager.beginTransaction()

        // 移除所有已存在的Fragment
        supportFragmentManager.fragments.forEach { fragment ->
            transaction.remove(fragment)
        }

        // 每次都创建新的Fragment实例，不使用持久化
        val fragment = when (tag) {
            TAG_HOME -> FrontPageFragment()
            TAG_FOCUS -> FocusOnFragment()
            TAG_SHOP -> MemberShipFragment()
            TAG_MINE -> PersonalFragment()
            else -> FrontPageFragment()
        }

        transaction.add(R.id.nav_host_fragment, fragment, tag)
        transaction.commit()
    }
}