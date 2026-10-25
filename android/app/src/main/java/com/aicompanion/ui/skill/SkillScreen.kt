/** 技能库页面：查看/新建/编辑/AI 生成/导入导出跨 App 自动化技能 */
package com.aicompanion.ui.skill

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aicompanion.network.ApiClient
import com.aicompanion.settings.SettingsManager
import com.aicompanion.skill.Skill
import com.aicompanion.skill.SkillCreator
import com.aicompanion.skill.SkillStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { SkillStore(context) }
    val settings = remember { SettingsManager(context) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var skills by remember { mutableStateOf(store.getAll()) }
    var detailSkill by remember { mutableStateOf<Skill?>(null) }
    var editorTarget by remember { mutableStateOf<Skill?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showAiCreator by remember { mutableStateOf(false) }
    var aiRequirement by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf("") }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf("") }

    fun refresh() {
        skills = store.getAll()
    }

    fun buildApi(): ApiClient? {
        if (settings.chatApiUrl.isBlank()) return null
        return ApiClient(settings.chatApiUrl, settings.chatApiKey, settings.chatModel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("技能库（${skills.size}）") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        editorTarget = null
                        showEditor = true
                    }) { Icon(Icons.Default.Add, contentDescription = "新建技能") }
                    IconButton(onClick = {
                        aiError = ""
                        showAiCreator = true
                    }) { Icon(Icons.Default.AutoAwesome, contentDescription = "AI 生成技能") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (toast.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(toast, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }

            // 开关与导入导出
            Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("启用技能库", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "开启后 AI 会在系统提示里看到技能清单，需要时自动读取正文",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.skillEnabled,
                            onCheckedChange = { settings.skillEnabled = it }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = {
                            val md = store.allAsMarkdown()
                            if (md.isBlank()) {
                                toast = "技能库为空，没有可导出的内容"
                            } else {
                                clipboard.setText(AnnotatedString(md))
                                toast = "已复制 ${skills.size} 个技能的 Markdown 到剪贴板"
                            }
                        }) { Text("导出全部") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { importText = ""; showImport = true }) { Text("导入") }
                    }
                }
            }

            if (skills.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有技能。\n点右上角「+」手写一个，或用「✨」让 AI 生成，\n也可以用「导入」粘贴别人分享的技能。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(skills, key = { it.id }) { skill ->
                        SkillCard(
                            skill = skill,
                            onOpen = { detailSkill = skill },
                            onToggle = { enabled ->
                                store.setEnabled(skill.id, enabled)
                                refresh()
                            },
                            onDelete = {
                                store.delete(skill.id)
                                refresh()
                                toast = "已删除：${skill.displayName}"
                            }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    // ===== 详情 =====
    detailSkill?.let { skill ->
        AlertDialog(
            onDismissRequest = { detailSkill = null },
            title = { Text(skill.displayName) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (skill.appName.isNotBlank()) {
                        Text("适用 App：${skill.appName} ${skill.appPackage}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("来源：${sourceLabel(skill.source)}   版本：v${skill.version}", style = MaterialTheme.typography.bodySmall)
                    if (skill.useCount > 0) {
                        Text("使用 ${skill.useCount} 次，成功率 ${skill.successRate}%", style = MaterialTheme.typography.bodySmall)
                    }
                    if (skill.description.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(skill.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            skill.content.ifBlank { "（正文为空）" },
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(skill.toMarkdown()))
                    toast = "已复制技能 Markdown"
                    detailSkill = null
                }) { Text("复制") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        editorTarget = skill
                        showEditor = true
                        detailSkill = null
                    }) { Text("编辑") }
                    TextButton(onClick = {
                        val api = buildApi()
                        if (api == null) {
                            toast = "未配置聊天 API，无法让 AI 改进"
                        } else {
                            scope.launch {
                                aiBusy = true
                                toast = "AI 正在改进「${skill.displayName}」…"
                                val result = SkillCreator.createFromDescription(
                                    context = context,
                                    apiClient = api,
                                    requirement = "请检查并改进这个技能，让它更准确、更可执行，尤其要补充失败兜底和危险环节说明",
                                    existing = skill
                                )
                                aiBusy = false
                                toast = result.fold(
                                    { "已更新：${it.displayName}（v${it.version}）" },
                                    { "改进失败：${it.message}" }
                                )
                                refresh()
                            }
                        }
                        detailSkill = null
                    }) { Text("AI 改进") }
                }
            }
        )
    }

    // ===== 手动编辑 =====
    if (showEditor) {
        SkillEditorDialog(
            initial = editorTarget,
            onDismiss = { showEditor = false },
            onSave = { skill ->
                store.save(skill)
                refresh()
                showEditor = false
                toast = "已保存：${skill.displayName}"
            }
        )
    }

    // ===== AI 生成 =====
    if (showAiCreator) {
        AlertDialog(
            onDismissRequest = { if (!aiBusy) showAiCreator = false },
            title = { Text("让 AI 生成技能") },
            text = {
                Column {
                    Text(
                        "描述你想让 AI 学会的操作，越具体越好。\n例如：「微信里给指定好友发送文字消息」「淘宝搜索商品并加入购物车」「设置里把亮度调到最低」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aiRequirement,
                        onValueChange = { aiRequirement = it },
                        label = { Text("需求描述") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        enabled = !aiBusy
                    )
                    if (aiError.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(aiError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (aiBusy) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("正在生成…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = aiRequirement.isNotBlank() && !aiBusy,
                    onClick = {
                        val api = buildApi()
                        if (api == null) {
                            aiError = "未配置聊天 API，请先到设置里配置"
                            return@TextButton
                        }
                        scope.launch {
                            aiBusy = true
                            aiError = ""
                            val screenContext = withContext(Dispatchers.IO) {
                                try { com.aicompanion.screen.AutoOperator.readScreenText() } catch (_: Exception) { "" }
                            }
                            val result = SkillCreator.createFromDescription(
                                context = context,
                                apiClient = api,
                                requirement = aiRequirement,
                                screenContext = screenContext.take(2000)
                            )
                            aiBusy = false
                            result.fold(
                                onSuccess = {
                                    toast = "已生成技能：${it.displayName}"
                                    showAiCreator = false
                                    aiRequirement = ""
                                    refresh()
                                },
                                onFailure = { aiError = "生成失败：${it.message}" }
                            )
                        }
                    }
                ) { Text(if (aiBusy) "生成中…" else "生成") }
            },
            dismissButton = {
                TextButton(enabled = !aiBusy, onClick = { showAiCreator = false }) { Text("取消") }
            }
        )
    }

    // ===== 导入 =====
    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入技能 Markdown") },
            text = {
                Column {
                    Text(
                        "粘贴技能 Markdown（支持多段，用 <!-- ===== SKILL SPLIT ===== --> 分隔）",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                        label = { Text("技能内容") }
                    )
                    TextButton(onClick = {
                        val t = clipboard.getText()?.text ?: ""
                        if (t.isBlank()) toast = "剪贴板为空" else importText = t
                    }) { Text("从剪贴板填入") }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = importText.isNotBlank(),
                    onClick = {
                        val imported = store.importFromMarkdown(importText)
                        refresh()
                        showImport = false
                        toast = "已导入 ${imported.size} 个技能"
                    }
                ) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(skill.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val scope = when {
                        skill.appPackage.isNotBlank() -> "${skill.appName.ifBlank { skill.appPackage }}"
                        skill.appName.isNotBlank() -> skill.appName
                        else -> "通用"
                    }
                    Text(
                        "$scope · ${sourceLabel(skill.source)}" +
                            if (skill.useCount > 0) " · 用${skill.useCount}次/成功${skill.successRate}%" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = skill.enabled, onCheckedChange = onToggle)
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除") }
            }
            if (skill.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(skill.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onOpen) { Text("查看正文") }
                if (skill.triggers.isNotEmpty()) {
                    Text(
                        "触发：${skill.triggers.take(4).joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillEditorDialog(
    initial: Skill?,
    onDismiss: () -> Unit,
    onSave: (Skill) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var appName by remember { mutableStateOf(initial?.appName ?: "") }
    var appPackage by remember { mutableStateOf(initial?.appPackage ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var triggers by remember { mutableStateOf(initial?.triggers?.joinToString("、") ?: "") }
    var content by remember {
        mutableStateOf(initial?.content ?: SkillCreator.createTemplate("新技能").content)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建技能" else "编辑技能") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("技能名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(appName, { appName = it }, label = { Text("适用 App（通用流程留空）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(appPackage, { appPackage = it }, label = { Text("包名（可选，如 com.tencent.mm）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(description, { description = it }, label = { Text("一句话描述（给 AI 判断是否加载）") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(triggers, { triggers = it }, label = { Text("触发词，用、分隔") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    content,
                    { content = it },
                    label = { Text("正文（Markdown 步骤说明）") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = initial ?: SkillCreator.createTemplate(name, appName, appPackage)
                    onSave(
                        base.copy(
                            name = name.trim(),
                            appName = appName.trim(),
                            appPackage = appPackage.trim(),
                            description = description.trim(),
                            scope = if (appName.isBlank() && appPackage.isBlank()) "global" else "app",
                            triggers = triggers.split('、', ',', '，')
                                .map { it.trim() }.filter { it.isNotBlank() },
                            content = content,
                            source = initial?.source ?: "manual"
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun sourceLabel(source: String): String = when (source) {
    "ai" -> "AI 生成"
    "auto" -> "自动归纳"
    else -> "手动"
}
