package com.wb.contacts.sms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wb.contacts.R
import com.wb.contacts.ui.ContactsAppTheme

/**
 * 极简的短信撰写界面。
 *
 * ## 为什么要有它
 *
 * Android 10+ 授予「默认短信应用」角色时，系统会检查清单里是否声明了
 * 四个短信组件：接收器、WAP Push 接收器、撰写 activity、回复 service。
 * 四个都齐了，角色请求才会被批准 —— 少一个，系统设置里就不会把本应用
 * 列进候选，写回短信这条路直接走不通。
 *
 * 本应用的主业是通讯录备份，不是短信应用，所以这里只做一个能发短信的
 * 最简界面，够系统验身就行。
 */
class ComposeSmsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recipient = intent?.data?.schemeSpecificPart?.trim().orEmpty()
        val presetBody = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        setContent {
            ContactsAppTheme {
                ComposeSmsScreen(
                    recipient = recipient,
                    presetBody = presetBody,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeSmsScreen(recipient: String, presetBody: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var to by remember { mutableStateOf(recipient) }
    var body by remember { mutableStateOf(presetBody) }
    var hint by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_compose_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                stringResource(R.string.sms_compose_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(stringResource(R.string.sms_compose_recipient)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.sms_compose_body)) },
                minLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            )

            if (hint.isNotEmpty()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Button(
                onClick = {
                    hint = context.getString(
                        if (send(context, to, body)) {
                            R.string.sms_compose_sent
                        } else {
                            R.string.sms_compose_failed
                        }
                    )
                },
                enabled = to.isNotBlank() && body.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .height(48.dp)
            ) { Text(stringResource(R.string.sms_compose_send)) }
        }
    }
}

/** 发送短信，成功返回 true */
private fun send(context: Context, to: String, body: String): Boolean {
    val manager = context.getSystemService(SmsManager::class.java) ?: return false
    return runCatching {
        // sendMultipartTextMessage 要的是 ArrayList<String>，不是 Array
        manager.sendMultipartTextMessage(
            to,
            null,
            arrayListOf(body),
            null,
            null
        )
    }.isSuccess
}
