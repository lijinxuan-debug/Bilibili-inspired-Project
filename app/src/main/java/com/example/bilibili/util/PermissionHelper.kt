package com.example.bilibili.util

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.example.bilibili.R
import com.example.bilibili.databinding.DialogPermissionSettingsBinding
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions

/**
 * 统一权限申请：永久拒绝时先弹说明框，用户点「去设置」再跳转系统权限页。
 */
object PermissionHelper {

    /** 选图 / 改头像 */
    fun requestGalleryImage(
        activity: Activity,
        onGranted: () -> Unit,
    ) {
        request(
            activity = activity,
            permissions = galleryImagePermissions(),
            deniedToast = activity.getString(R.string.permission_gallery_denied),
            onGranted = onGranted,
        )
    }

    /** 发布视频：相册视频 + 图片 + 相机 */
    fun requestPublishVideo(
        activity: Activity,
        onGranted: () -> Unit,
    ) {
        request(
            activity = activity,
            permissions = publishVideoPermissions(),
            deniedToast = activity.getString(R.string.permission_publish_denied),
            onGranted = onGranted,
        )
    }

    private fun galleryImagePermissions(): List<String> = listOf(
        Permission.READ_MEDIA_IMAGES,
    )

    private fun publishVideoPermissions(): List<String> = listOf(
        Permission.READ_MEDIA_VIDEO,
        Permission.READ_MEDIA_IMAGES,
        Permission.CAMERA,
    )

    private fun request(
        activity: Activity,
        permissions: List<String>,
        deniedToast: String,
        onGranted: () -> Unit,
    ) {
        XXPermissions.with(activity)
            .permission(permissions)
            .request(object : OnPermissionCallback {
                override fun onGranted(granted: MutableList<String>, allGranted: Boolean) {
                    if (allGranted) {
                        onGranted()
                    }
                }

                override fun onDenied(denied: MutableList<String>, doNotAskAgain: Boolean) {
                    if (doNotAskAgain) {
                        showGoSettingsDialog(activity, denied)
                    } else {
                        ToastUtils.showShort(activity, deniedToast)
                    }
                }
            })
    }

    fun showGoSettingsDialog(activity: Activity, permissions: List<String>) {
        val binding = DialogPermissionSettingsBinding.inflate(activity.layoutInflater)
        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(true)

        binding.tvPermissionCancel.setOnClickListener { dialog.dismiss() }
        binding.tvPermissionGoSettings.setOnClickListener {
            dialog.dismiss()
            XXPermissions.startPermissionActivity(activity, permissions)
        }

        dialog.show()
    }
}
