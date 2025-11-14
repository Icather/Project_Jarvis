package icather.pages.dev

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import icather.pages.dev.db.ApiConfig
import icather.pages.dev.db.AppDatabase
import icather.pages.dev.db.Conversation
import icather.pages.dev.db.Message
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import kotlinx.coroutines.launch

data class ChatHistoryBundle(val conversations: List<Conversation>, val messages: List<Message>)

class SettingsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private var jsonToExport: String? = null

    private val exportApiLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportApiConfigsToUri(it) }
    }

    private val importApiLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importApiConfigsFromUri(it) }
    }

    private val exportChatHistoryLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { exportChatHistoryToUri(it) }
    }

    private val importChatHistoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importChatHistoryFromUri(it) }
    }

    private val selectConversationsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val selectedIds = result.data?.getLongArrayExtra("selected_ids")?.toList()
            if (selectedIds != null && selectedIds.isNotEmpty()) {
                lifecycleScope.launch {
                    val selectedConversations = db.conversationDao().getConversationsByIds(selectedIds)
                    val selectedMessages = db.messageDao().getMessagesForConversationIds(selectedIds)
                    val bundle = ChatHistoryBundle(selectedConversations, selectedMessages)
                    val json = Gson().toJson(bundle)
                    jsonToExport = json

                    val hash = sha256(json).substring(0, 8)
                    val fileName = "聊天记录_${hash}.json"
                    exportChatHistoryLauncher.launch(fileName)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        db = AppDatabase.getInstance(this)

        findViewById<TextView>(R.id.apiConfig).setOnClickListener {
            startActivity(Intent(this, ApiConfigActivity::class.java))
        }

        findViewById<TextView>(R.id.exportApiConfigs).setOnClickListener {
            exportApiConfigs()
        }

        findViewById<TextView>(R.id.importApiConfigs).setOnClickListener {
            importApiConfigs()
        }

        findViewById<TextView>(R.id.exportChatHistory).setOnClickListener {
            showExportChatHistoryDialog()
        }

        findViewById<TextView>(R.id.importChatHistory).setOnClickListener {
            showImportChatHistoryDialog()
        }
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

    private fun exportApiConfigs() {
        lifecycleScope.launch {
            val apiConfigs = db.apiConfigDao().getAllOnce()
            if (apiConfigs.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.no_history_to_export, Toast.LENGTH_SHORT).show()
                return@launch
            }
            val json = Gson().toJson(apiConfigs)
            jsonToExport = json
            val hash = sha256(json).substring(0, 8)
            val fileName = "配置API_${hash}.json"
            exportApiLauncher.launch(fileName)
        }
    }

    private fun importApiConfigs() {
        importApiLauncher.launch(arrayOf("application/json"))
    }

    private fun showExportChatHistoryDialog() {
        val options = arrayOf(getString(R.string.export_all), getString(R.string.select_and_export))
        AlertDialog.Builder(this)
            .setTitle(R.string.export_chat_history)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportAllChatHistory()
                    1 -> selectConversationsToExport()
                }
            }
            .show()
    }

    private fun selectConversationsToExport() {
        val intent = Intent(this, HistoryActivity::class.java)
        intent.putExtra("is_selection_mode", true)
        selectConversationsLauncher.launch(intent)
    }

    private fun showImportChatHistoryDialog() {
        importChatHistoryLauncher.launch(arrayOf("application/json"))
    }

    private fun exportAllChatHistory() {
        lifecycleScope.launch {
            val conversations = db.conversationDao().getAllConversations()
            val messages = db.messageDao().getAllMessages()
            if (conversations.isEmpty() && messages.isEmpty()) {
                Toast.makeText(this@SettingsActivity, R.string.no_history_to_export, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val bundle = ChatHistoryBundle(conversations, messages)
            val json = Gson().toJson(bundle)
            jsonToExport = json

            val hash = sha256(json).substring(0, 8)
            val fileName = "聊天记录_${hash}.json"
            exportChatHistoryLauncher.launch(fileName)
        }
    }

    private fun exportApiConfigsToUri(uri: Uri) {
        val json = jsonToExport ?: return
        lifecycleScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { it.writer().write(json) }
                Toast.makeText(this@SettingsActivity, R.string.export_successful, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                jsonToExport = null // Clean up
            }
        }
    }

    private fun importApiConfigsFromUri(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).readText() } ?: return@launch
                val type = object : TypeToken<List<ApiConfig>>() {}.type
                val importedConfigs: List<ApiConfig> = Gson().fromJson(json, type)
                db.apiConfigDao().insertAll(importedConfigs.map { it.copy(id = 0) })
                Toast.makeText(this@SettingsActivity, "Import successful. ${importedConfigs.size} configurations imported.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.import_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportChatHistoryToUri(uri: Uri) {
        val json = jsonToExport ?: return
        lifecycleScope.launch {
            try {
                contentResolver.openOutputStream(uri)?.use { it.writer().write(json) }
                Toast.makeText(this@SettingsActivity, R.string.export_successful, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.export_failed, e.message), Toast.LENGTH_SHORT).show()
            } finally {
                jsonToExport = null // Clean up to prevent memory leaks
            }
        }
    }

    private fun importChatHistoryFromUri(uri: Uri) {
        val options = arrayOf(getString(R.string.overwrite_import), getString(R.string.incremental_import))
        AlertDialog.Builder(this)
            .setTitle(R.string.import_mode)
            .setItems(options) { _, which ->
                executeImport(uri, which == 0)
            }
            .show()
    }

    private fun executeImport(uri: Uri, overwrite: Boolean) {
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it)).readText() } ?: return@launch
                val type = object : TypeToken<ChatHistoryBundle>() {}.type
                val bundle: ChatHistoryBundle = Gson().fromJson(json, type)

                if (overwrite) {
                    db.conversationDao().clearAll()
                    db.messageDao().clearAll()
                }

                val idMap = mutableMapOf<Long, Long>()
                bundle.conversations.forEach { conversation ->
                    val oldId = conversation.id
                    val newId = db.conversationDao().insert(conversation.copy(id = 0))
                    idMap[oldId] = newId
                }

                bundle.messages.forEach { message ->
                    val newConversationId = idMap[message.conversationId] ?: message.conversationId
                    db.messageDao().insert(message.copy(id = 0, conversationId = newConversationId))
                }

                Toast.makeText(this@SettingsActivity, getString(R.string.import_successful_messages, bundle.conversations.size, bundle.messages.size), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.import_failed, e.message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }
}
