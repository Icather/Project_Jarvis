package icather.pages.dev

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class AboutActivity : AppCompatActivity() {

    private var downloadId: Long = -1
    private lateinit var downloadManager: DownloadManager

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                val downloadedFileUri = downloadManager.getUriForDownloadedFile(downloadId)
                if (downloadedFileUri != null) {
                    installApk(downloadedFileUri)
                } else {
                    Toast.makeText(context, R.string.download_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val versionTextView: TextView = findViewById(R.id.app_version)
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            versionTextView.text = getString(R.string.version_name, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        val projectHome: TextView = findViewById(R.id.project_home)
        projectHome.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://github.com/Icather/Project_Necore".toUri())
            startActivity(intent)
        }

        val openSourceLicenses: TextView = findViewById(R.id.open_source_licenses)
        openSourceLicenses.setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }

        val checkForUpdates: TextView = findViewById(R.id.check_for_updates)
        checkForUpdates.setOnClickListener {
            Toast.makeText(this, R.string.checking_for_updates, Toast.LENGTH_SHORT).show()
            checkForUpdates()
        }

        downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(onDownloadComplete)
    }

    private fun checkForUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileContent = URL(UPDATE_URL).readText()
                val majorVersion = fileContent.substringAfter("majorVersion = ").substringBefore("\n").trim().toInt()
                val minorVersion = fileContent.substringAfter("minorVersion = ").substringBefore("\n").trim().toInt()
                val patchVersion = fileContent.substringAfter("patchVersion = ").substringBefore("\n").trim().toInt()
                val latestVersionName = "$majorVersion.$minorVersion.$patchVersion"

                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                val currentVersionName = packageInfo.versionName

                withContext(Dispatchers.Main) {
                    if (currentVersionName != null && isNewerVersion(latestVersionName, currentVersionName)) {
                        downloadAndInstallUpdate()
                    } else {
                        Toast.makeText(this@AboutActivity, R.string.latest_version, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AboutActivity, R.string.check_for_updates_failed, Toast.LENGTH_SHORT).show()
                }
                e.printStackTrace()
            }
        }
    }

    private fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
        val latestParts = latestVersion.split(".").map { it.toInt() }
        val currentParts = currentVersion.split(".").map { it.toInt() }

        val commonLength = minOf(latestParts.size, currentParts.size)

        for (i in 0 until commonLength) {
            if (latestParts[i] > currentParts[i]) {
                return true
            }
            if (latestParts[i] < currentParts[i]) {
                return false
            }
        }

        return latestParts.size > currentParts.size
    }


    private fun downloadAndInstallUpdate() {
        val destination = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app-debug.apk")

        val request = DownloadManager.Request(APK_URL.toUri())
            .setTitle(getString(R.string.updating_necore))
            .setDescription(getString(R.string.downloading_update))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))

        downloadId = downloadManager.enqueue(request)
    }

    private fun installApk(uri: Uri) {
        val contentUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", File(uri.path!!))
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val UPDATE_URL = "https://gitee.com/Icather/Project_Necore/raw/main/Project_Necore/app/build.gradle.kts"
        private const val APK_URL = "https://gitee.com/Icather/Project_Necore/raw/main/_update/app-debug.apk"
    }
}
