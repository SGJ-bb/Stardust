package com.aicompanion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.aicompanion.theme.StradustTheme
import com.aicompanion.ui.ChatMessage
import com.aicompanion.ui.chat.ChatScreen
import com.aicompanion.ui.settings.SettingsScreen
import com.aicompanion.ui.diary.DiaryScreen
import com.aicompanion.ui.diary.DiarySearchScreen
import com.aicompanion.ui.virtualworld.VirtualWorldInfo
import com.aicompanion.ui.virtualworld.VirtualWorldScreen
import com.aicompanion.ui.virtualworld.VwImageUploadScreen
import com.aicompanion.ui.album.AlbumScreen
import com.aicompanion.ui.album.AlbumGenScreen
import com.aicompanion.ui.checkin.CheckInScreen
import com.aicompanion.ui.achievement.AchievementScreen
import com.aicompanion.ui.profile.ProfileScreen
import com.aicompanion.ui.groupchat.GroupChatListScreen
import com.aicompanion.ui.groupchat.GroupChatInfo
import com.aicompanion.ui.groupchat.GroupChatScreen
import com.aicompanion.ui.groupchat.GroupChatSettingsScreen
import com.aicompanion.ui.groupchat.GroupMessage
import com.aicompanion.ui.home.HomeScreen
import com.aicompanion.ui.home.PersonaCard
import com.aicompanion.ui.phonecall.PhoneCallScreen
import com.aicompanion.ui.components.BottomNavItem
import com.aicompanion.ui.components.StradustBottomBar
import com.aicompanion.ui.components.shouldShowBottomNav
// 新增页面导入
import com.aicompanion.ui.capsule.TimeCapsuleScreen
import com.aicompanion.ui.milestone.MilestoneScreen
import com.aicompanion.ui.wakeup.WakeUpTaskScreen
import com.aicompanion.ui.growth.GrowthScreen
import com.aicompanion.ui.stats.PersonaStatsScreen
import com.aicompanion.ui.favorites.FavoritesScreen
import com.aicompanion.ui.schedule.ScheduleScreen
import com.aicompanion.ui.moments.MemorableMomentsScreen
import com.aicompanion.ui.sticker.StickerManagerScreen
import com.aicompanion.ui.calendar.CalendarScreen
import com.aicompanion.ui.nickname.NicknameScreen
import com.aicompanion.ui.ilink.IlinkScreen
import com.aicompanion.ui.localmodel.LocalModelScreen
import com.aicompanion.ui.memory.MemoryPoolScreen
import com.aicompanion.ui.character.CharacterCardScreen
import com.aicompanion.ui.character.WorldBookScreen
import com.aicompanion.ui.pixelpet.PixelPetScreen
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.aicompanion.capsule.TimeCapsuleManager
import com.aicompanion.milestone.MilestoneManager
import com.aicompanion.wakeup.WakeUpTaskManager
import com.aicompanion.gamify.GrowthManager
import com.aicompanion.affection.AffectionManager
import com.aicompanion.persona.PersonaManager

object StradustDestinations {
    const val HOME = "home"
    const val CHAT = "chat"
    const val SETTINGS = "settings"
    const val DIARY = "diary"
    const val VIRTUAL_WORLD = "virtual_world"
    const val ALBUM = "album"
    const val CHECK_IN = "check_in"
    const val ACHIEVEMENT = "achievement"
    const val PROFILE = "profile"
    const val GROUP_CHAT_LIST = "group_chat_list"
    const val GROUP_CHAT = "group_chat/{groupId}"
    const val PHONE_CALL = "phone_call"

    // ===== 新增页面路由 =====
    const val TIME_CAPSULE = "time_capsule"
    const val MILESTONE = "milestone"
    const val WAKEUP_TASK = "wakeup_task"
    const val GROWTH = "growth"
    const val PERSONA_STATS = "persona_stats"
    const val PERSONA_STATS_WITH_ID = "persona_stats/{personaId}"
    const val FAVORITES = "favorites"
    const val FAVORITES_WITH_ID = "favorites/{personaId}"
    const val SCHEDULE = "schedule"
    const val MEMORABLE_MOMENTS = "memorable_moments"
    const val DIARY_SEARCH = "diary_search"
    const val STICKER_MANAGER = "sticker_manager"
    const val ALBUM_GEN = "album_gen"
    const val GROUP_CHAT_SETTINGS = "group_chat_settings/{groupId}"
    const val VW_IMAGE_UPLOAD = "vw_image_upload"
    const val CALENDAR = "calendar"
    const val NICKNAME = "nickname"
    const val ILINK = "ilink"
    const val LOCAL_MODEL = "local_model"
    const val MEMORY_POOL = "memory_pool"
    const val CHARACTER_CARD = "character_card"
    const val CHARACTER_CARD_CREATE = "character_card_create"  // 直接进入创建模式
    const val WORLD_BOOK = "world_book"
    const val PIXEL_PET = "pixel_pet"

    /**
     * MainActivity → Compose UI 的桥接回调接口
     * 由 MainActivity 实现，传入 NavHost，再分发给各个 Screen
     */
    interface AppHost {
        /** 获取当前聊天消息列表 */
        fun provideChatMessages(): List<ChatMessage>
        /** 发送文本消息 */
        fun sendMessage(text: String)
        /** 开始录音 */
        fun startVoice()
        /** 停止录音 */
        fun stopVoice()
        /** 当前是否正在录音 */
        fun isVoiceRecording(): Boolean
        /** 导航到功能面板某项 */
        fun onFeatureClick(index: Int)
        /** 获取好感度信息：(current, max, levelText) */
        fun getAffectionInfo(): Triple<Int, Int, String>
        /** 获取天气信息：(weatherText, daysText) */
        fun getWeatherInfo(): Pair<String, String>
        /** 获取 AI 名称 */
        fun getAiName(): String
        /** 获取 AI 头像路径（用于 ChatScreen 显示） */
        fun getAiAvatarPath(): String? = null
        /** 获取用户头像路径（用于 ProfileScreen 显示） */
        fun getUserAvatarPath(): String? = null
        /** 选择 AI 头像（启动相册选择器，写入 AvatarManager） */
        fun pickAiAvatar() {}
        /** 选择用户头像（启动相册选择器，写入 AvatarManager） */
        fun pickUserAvatar() {}
        /** 获取聊天壁纸路径（用于 ChatScreen 背景） */
        fun getWallpaperPath(): String? = null
        /** 获取相处天数 */
        fun getDaysTogether(): Int
        /** 消息版本号（每次消息变化时递增，触发 Compose 重组） */
        fun getMessageVersion(): Int
        /** 消息版本号的 Compose State（变化时自动触发重组） */
        val messageVersionState: State<Int> get() = mutableStateOf(0)
        /** 当前是否正在等待 AI 回复（显示打字指示器） */
        fun isTyping(): Boolean
        /** 打字状态的 Compose State */
        val isTypingState: State<Boolean> get() = mutableStateOf(false)
        /** 当前是否正在加载中（显示加载圈） */
        fun isLoading(): Boolean
        /** 加载状态的 Compose State */
        val isLoadingState: State<Boolean> get() = mutableStateOf(false)
        /** 全局数据版本号（任何页面数据变化时递增，触发所有 Composable 重组） */
        val dataVersionState: State<Int> get() = mutableStateOf(0)
        /** 切换消息收藏 */
        fun toggleFavorite(message: ChatMessage)
        /** 设置消息反馈（点赞=true, 踩=false） */
        fun setFeedback(position: Int, isLike: Boolean)
        /** 选择表情包 */
        fun pickSticker()
        /** 选择图片上传 */
        fun pickImage()
        /** 打电话 */
        fun phoneCall()
        /** 切换壁纸 */
        fun changeWallpaper()
        /** 引用回复：用户长按消息触发引用回复（保存被引用消息，发送时携带） */
        fun onReplyToMessage(message: ChatMessage)
        /** 删除单条消息 */
        fun deleteMessage(message: ChatMessage)
        /** 重新生成AI回复（删除AI消息后重新请求） */
        fun regenerateMessage(message: ChatMessage)
        /** 编辑用户消息并重发（删除用户消息，发送新内容） */
        fun editAndResendMessage(message: ChatMessage, newText: String)
        /** 获取群聊列表数据（用于 GroupChatListScreen） */
        fun getGroupChatList(): List<GroupChatInfo>
        /** 获取指定群聊的消息列表（用于 GroupChatScreen） */
        fun getGroupMessages(groupId: String): List<GroupMessage>
        /** 发送群聊消息（用于 GroupChatScreen） */
        fun sendGroupMessage(groupId: String, text: String)
        /** 创建新群聊：返回新群 ID，失败返回 null */
        fun createGroup(name: String, memberPersonaIds: List<String>): String?
        /** 删除指定群聊 */
        fun deleteGroup(groupId: String)
        /** 获取指定群聊的成员名称列表（用于 GroupChatScreen 顶栏与气泡颜色分配） */
        fun getGroupMemberNames(groupId: String): List<String>
        /** 获取指定群聊的打字状态（AI 回复链路期间为 true，用于显示"正在输入..."指示器） */
        fun getGroupTypingState(groupId: String): androidx.compose.runtime.State<Boolean> = androidx.compose.runtime.mutableStateOf(false)
        /** 获取指定群聊的发言模式（manual/ai_judge/round_robin） */
        fun getGroupSpeakMode(groupId: String): String = "round_robin"
        /** 设置指定群聊的发言模式 */
        fun setGroupSpeakMode(groupId: String, mode: String) {}
        /** 获取指定群聊的成员 personaId 列表（用于手动模式成员选择） */
        fun getGroupMemberPersonaIds(groupId: String): List<String> = emptyList()
        /** 获取指定群聊手动模式下已选中的成员 personaId 集合 */
        fun getManualSelectedIds(groupId: String): Set<String> = emptySet()
        /** 设置指定群聊手动模式下已选中的成员 personaId 集合 */
        fun setManualSelectedIds(groupId: String, ids: Set<String>) {}

        /** 获取虚拟世界状态 */
        fun getVirtualWorldState(): VirtualWorldInfo

        /** 切换虚拟场景 */
        fun changeVirtualScene(sceneIndex: Int)

        /** 切换模拟状态（开始/暂停） */
        fun toggleVirtualSimulation()

        /** 虚拟世界互动操作 */
        fun onVirtualWorldInteraction(action: String)

        /** 获取所有角色卡片（用于 HomeScreen） */
        fun getPersonas(): List<PersonaCard>
        /** 显示添加角色对话框/页面 */
        fun showAddPersonaDialog()
        /** 导航到指定角色的聊天界面 */
        fun navigateToPersonaChat(personaId: String)
        /** 打开动态页面 */
        fun openMoments()
        /** 打开日记页面（需指定角色 ID） */
        fun openDiary(personaId: String)
        /** 打开角色档案页面（需指定角色 ID） */
        fun openProfile(personaId: String)

        // ===== 签到功能桥接（CheckInScreen） =====
        /** 获取连续签到天数 */
        fun getCheckInStreak(): Int
        /** 今天是否已签到 */
        fun isCheckedInToday(): Boolean
        /** 获取总签到次数 */
        fun getTotalCheckIns(): Int
        /** 获取已签到日期集合 */
        fun getCheckedDates(): Set<String>
        /** 执行签到操作 */
        fun performCheckIn()

        // ===== 通话界面状态桥接（PhoneCallScreen） =====
        /** 获取通话中 AI 名称 */
        fun getCallPersonaName(): String
        /** 通话是否活跃 */
        fun isCallActive(): Boolean
        /** 获取通话状态文本 */
        fun getCallStatus(): String
        /** 获取通话时长(毫秒) */
        fun getCallDurationMs(): Long
        /** 获取通话转写文本 */
        fun getCallTranscript(): String
        /** 是否静音 */
        fun isCallMuted(): Boolean
        /** 是否开启扬声器 */
        fun isCallSpeakerOn(): Boolean
        /** 获取波形模式 (0=IDLE, 1=LISTENING, 2=AI_SPEAKING, 3=MUTED) */
        fun getCallWaveformMode(): Int
        /** 挂断电话 */
        fun hangUp()
        /** 切换静音 */
        fun toggleCallMute()
        /** 切换扬声器 */
        fun toggleCallSpeaker()

        /** 获取日记列表（用于 DiaryScreen） */
        fun getDiaryEntries(): List<com.aicompanion.ui.diary.DiaryEntry>
        /** 写新日记（content 为用户输入内容，由 Compose 内联对话框触发） */
        fun addDiary(content: String)
        /** 点击日记条目（现已由 Compose 内联对话框处理，保留以便扩展） */
        fun onDiaryClick(diaryId: Int)
        /** 更新已有日记（id 为 UI 层 DiaryEntry.id，content 为新内容） */
        fun updateDiary(id: Long, content: String) {}
        /** 删除日记条目（按日期字符串定位） */
        fun deleteDiary(date: String)

        // ===== 设置功能桥接（SettingsScreen） =====
        /** 获取 API 地址 */
        fun getApiUrl(): String
        /** 获取 API Key */
        fun getApiKey(): String
        /** 获取模型名称 */
        fun getModel(): String
        /** 获取温度值 */
        fun getTemperature(): Float
        /** 获取最大 Token 值 */
        fun getMaxTokens(): Int
        /** TTS 是否启用 */
        fun isTtsEnabled(): Boolean
        /** ASR 是否启用 */
        fun isAsrEnabled(): Boolean
        /** 获取语速 */
        fun getSpeechRate(): Float
        /** 获取音色名称 */
        fun getVoice(): String
        /** 保存 API 地址 */
        fun saveApiUrl(url: String)
        /** 保存 API Key */
        fun saveApiKey(key: String)
        /** 保存模型选择 */
        fun saveModel(model: String)
        /** 保存温度值 */
        fun saveTemperature(temperature: Float)
        /** 保存最大 Token 值 */
        fun saveMaxTokens(maxTokens: Int)
        /** 保存 TTS 开关状态 */
        fun saveTtsEnabled(enabled: Boolean)
        /** 保存 ASR 开关状态 */
        fun saveAsrEnabled(enabled: Boolean)
        /** 保存语速 */
        fun saveSpeechRate(rate: Float)
        /** 保存音色选择 */
        fun saveVoice(voice: String)

        // ===== 成就功能桥接（AchievementScreen） =====
        /** 获取成就列表（用于 AchievementScreen，从 AchievementManager 读取真实数据） */
        fun getAchievements(): List<com.aicompanion.models.Achievement>

        // ===== 相册功能桥接（AlbumScreen） =====
        /** 获取纪念相册照片列表（从 MemorialAlbumManager 读取真实图片路径） */
        fun getAlbumPhotos(): List<com.aicompanion.ui.album.AlbumPhotoData>

        /** 获取个人中心页面数据（用于 ProfileScreen） */
        fun getProfileData(): com.aicompanion.ui.profile.ProfileData
        /** 退出登录：清除会话/凭据，重置状态，返回首页或登录页 */
        fun logout()
        /** 获取应用版本名称（用于关于对话框） */
        fun getAppVersionName(): String = "1.0.0"

        // ===== Live2D 桥接（ChatScreen + SettingsScreen） =====
        /** Live2D 是否启用 */
        fun isLive2dEnabled(): Boolean = false
        /** 获取 Live2DWebView 视图（懒创建，供 AndroidView 嵌入） */
        fun getLive2DView(): android.view.View? = null
        /** 加载/切换 Live2D 模型（按 active_model_path 重新加载） */
        fun loadLive2DModel() {}
        /** 暂停 Live2D 渲染（离开聊天页时调用） */
        fun pauseLive2D() {}
        /** 恢复 Live2D 渲染（进入聊天页时调用） */
        fun resumeLive2D() {}
        /** 获取所有可用 Live2D 模型列表（用于设置页切换） */
        fun getLive2DModels(): List<com.aicompanion.models.Live2DModel> = emptyList()
        /** 获取当前激活的 Live2D 模型 ID */
        fun getCurrentLive2DModelId(): String = ""
        /** 切换 Live2D 模型 */
        fun setLive2DModel(modelId: String) {}
        /** 获取 Live2D 模型缩放（0.3 - 3.0） */
        fun getLive2DScale(): Float = 1f
        /** 实时设置 Live2D 模型缩放并持久化 */
        fun setLive2DScale(scale: Float) {}

        // ===== AI 预测回复桥接（ChatScreen） =====
        /** 获取当前预测回复列表（变化时触发重组） */
        val predictionsState: State<List<String>> get() = mutableStateOf(emptyList())
        /** 触发预测回复生成（AI 回复后调用） */
        fun triggerPredictions() {}
        /** 清除预测回复 */
        fun clearPredictions() {}
    }
}

// 底部导航栏项定义（文件级常量，避免每次重组重建）
private val bottomNavItems = listOf(
    BottomNavItem(StradustDestinations.HOME, "首页", Icons.Default.Home),
    BottomNavItem(StradustDestinations.CHAT, "聊天", Icons.Default.ChatBubble),
    BottomNavItem(StradustDestinations.DIARY, "日记", Icons.Default.EditNote),
    BottomNavItem(StradustDestinations.ALBUM, "相册", Icons.Default.PhotoLibrary),
    BottomNavItem(StradustDestinations.PROFILE, "我的", Icons.Default.Person),
)

@Composable
fun StradustNavHost(
    navController: NavHostController = rememberNavController(),
    host: StradustDestinations.AppHost? = null,
    onNavControllerReady: ((NavHostController) -> Unit)? = null,
) {
    // 回调通知外部 navController 已就绪
    LaunchedEffect(navController) {
        onNavControllerReady?.invoke(navController)
    }

    // 注意：dataVersion 不再在 NavHost 级别读取，避免整个 NavHost 因 dataVersion 变化而重组。
    // 各 Screen 如需响应数据变化，应在各自 composable 内部读取对应的 State。

    // 获取当前路由（用于底部导航高亮和显隐控制）
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 是否显示底部导航栏（全屏页面隐藏：phone_call/group_chat/chat）
    val showBottomBar = shouldShowBottomNav(currentRoute)

    // 底部 tab 统一导航逻辑：popUpTo 起点 + saveState + restoreState + launchSingleTop
    // 用于底部 5 个主 tab（HOME/CHAT/DIARY/ALBUM/PROFILE）以及从 HomeScreen 跳到这些 tab 的入口
    // 使用 rememberUpdatedState 稳定化 lambda，避免 currentRoute 变化导致 lambda 重建
    val currentRouteForNav by rememberUpdatedState(currentRoute)
    val navigateToBottomTab: (String) -> Unit = remember {
        { route: String ->
            if (currentRouteForNav != route) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    // ─── 页面切换动画定义 ──────────────────────────────
    // Tab 切换：无动画（instant），避免转场期间新旧页面双倍渲染导致卡顿
    val tabEnterTransition = EnterTransition.None
    val tabExitTransition = ExitTransition.None

    // 子页面推进：仅横向滑动（200ms），去掉 fade 避免双动画叠加
    val pageEnterTransition = slideInHorizontally(
        initialOffsetX = { it / 3 }, animationSpec = tween(200),
    )
    val pageExitTransition = slideOutHorizontally(
        targetOffsetX = { -(it / 4) }, animationSpec = tween(150),
    )

    // ===== 布局结构（Box 替代 Scaffold） =====
    // adjustResize 模式下系统自动缩小窗口避让键盘，Compose 无需 imePadding()。
    // bottomBar 叠加在 content 之上（不占布局空间）。
    // 有 bottomBar 的页面额外加 bottomBar 高度的 padding。
    val bottomBarHeight = 80.dp  // Material3 NavigationBar 标准高度

    Box(modifier = Modifier.fillMaxSize()) {
        // ===== content 层：全屏，避开 bottomBar（键盘由系统 adjustResize 处理） =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showBottomBar) Modifier.padding(bottom = bottomBarHeight) else Modifier)
        ) {
            NavHost(
                navController = navController,
                startDestination = StradustDestinations.HOME,
                enterTransition = { tabEnterTransition },
                exitTransition = { tabExitTransition },
                popEnterTransition = { tabEnterTransition },
                popExitTransition = { tabExitTransition },
            ) {
        composable(StradustDestinations.HOME) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            // 用 remember(dataVersion) 缓存 host 数据调用，避免每次重组都创建新列表引用导致子组件级联重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val personas = remember(dataVersion) { host?.getPersonas() ?: emptyList() }
            val wallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }
            HomeScreen(
                personas = personas,
                wallpaperPath = wallpaperPath,
                onPersonaClick = { personaId ->
                    host?.navigateToPersonaChat(personaId)
                },
                onAddPersona = {
                    host?.showAddPersonaDialog()
                },
                onMomentsClick = {
                    host?.openMoments()
                },
                onDiaryClick = { personaId ->
                    host?.openDiary(personaId)
                },
                onAlbumClick = {
                    // 相册是底部 tab 之一，使用统一底部 tab 导航（保留状态、避免栈堆积）
                    navigateToBottomTab(StradustDestinations.ALBUM)
                },
                onAchievementClick = {
                    navController.navigate(StradustDestinations.ACHIEVEMENT) {
                        launchSingleTop = true
                    }
                },
                onVirtualWorldClick = {
                    navController.navigate(StradustDestinations.VIRTUAL_WORLD) {
                        launchSingleTop = true
                    }
                },
                onCheckInClick = {
                    navController.navigate(StradustDestinations.CHECK_IN) {
                        launchSingleTop = true
                    }
                },
                onGroupChatClick = {
                    navController.navigate(StradustDestinations.GROUP_CHAT_LIST) {
                        launchSingleTop = true
                    }
                },
                onViewAllPersonas = {
                    navController.navigate(StradustDestinations.CHARACTER_CARD) { launchSingleTop = true }
                },
                onNavigate = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(StradustDestinations.CHAT) {
            // 关键：通过 State 读取触发 Compose 自动重组
            val messageVersion = host?.messageVersionState?.value ?: 0
            val isTyping = host?.isTypingState?.value ?: false
            val isLoading = host?.isLoadingState?.value ?: false
            val dataVersion = host?.dataVersionState?.value ?: 0

            // 缓存 host 数据调用，避免每次重组创建新列表/对象引用
            val chatMessages = remember(messageVersion) { host?.provideChatMessages() ?: emptyList() }
            val aiName = remember(dataVersion) { host?.getAiName() ?: "星尘" }
            val aiAvatarPath = remember(dataVersion) { host?.getAiAvatarPath() }
            val chatWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }
            val daysTogether = remember(dataVersion) { host?.getDaysTogether() ?: 1 }
            val affectionInfo = remember(dataVersion) { host?.getAffectionInfo() ?: Triple(0, 100, "Lv.1") }
            val weatherInfo = remember(dataVersion) { host?.getWeatherInfo() ?: Pair("☀️", "第1天") }

            ChatScreen(
                messages = chatMessages,
                isTyping = isTyping,
                isLoading = isLoading,
                onSendMessage = { text -> host?.sendMessage(text) },
                onStartVoice = { host?.startVoice() },
                onStopVoice = { host?.stopVoice() },
                isRecording = host?.isVoiceRecording() ?: false,
                onFeatureClick = { index -> host?.onFeatureClick(index) },
                aiName = aiName,
                aiAvatarPath = aiAvatarPath,
                wallpaperPath = chatWallpaperPath,
                daysTogether = daysTogether,
                affectionInfo = affectionInfo,
                weatherInfo = weatherInfo,
                messageVersion = messageVersion,
                onFavoriteToggle = { msg -> host?.toggleFavorite(msg) },
                onFeedback = { msg, feedback ->
                    val pos = chatMessages.indexOfFirst { it.id == msg.id }
                    if (pos >= 0) host?.setFeedback(pos, feedback == 1)
                },
                onPickSticker = { host?.pickSticker() },
                onPickImage = { host?.pickImage() },
                onPhoneCall = { host?.phoneCall() },
                onChangeWallpaper = { host?.changeWallpaper() },
                onReplyClick = { msg -> host?.onReplyToMessage(msg) },
                onRegenerate = { msg -> host?.regenerateMessage(msg) },
                onEditAndResend = { msg, newText -> host?.editAndResendMessage(msg, newText) },
                onDeleteMessage = { msg -> host?.deleteMessage(msg) },
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                live2dEnabled = host?.isLive2dEnabled() ?: false,
                live2dViewProvider = { host?.getLive2DView() },
                onLive2dResume = { host?.resumeLive2D() },
                onLive2dPause = { host?.pauseLive2D() },
                predictions = host?.predictionsState?.value ?: emptyList(),
                onPredictionClick = { pred ->
                    // 点击预测回复直接发送
                    host?.sendMessage(pred)
                    host?.clearPredictions()
                },
            )
        }
        // Compose版设置页面（主入口）
        composable(
            route = StradustDestinations.SETTINGS,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            // 稳定化 callbacks 对象，避免每次重组创建新匿名对象触发 SettingsScreen 重组
            val stableCallbacks = remember {
                object : com.aicompanion.ui.settings.SettingsScreenCallbacks {
                    override fun onApiUrlChange(url: String) { host?.saveApiUrl(url) }
                    override fun onApiKeyChange(key: String) { host?.saveApiKey(key) }
                    override fun onModelChange(model: String) { host?.saveModel(model) }
                    override fun onTemperatureChange(temperature: Float) { host?.saveTemperature(temperature) }
                    override fun onMaxTokensChange(maxTokens: Int) { host?.saveMaxTokens(maxTokens) }
                    override fun onTtsEnabledChange(enabled: Boolean) { host?.saveTtsEnabled(enabled) }
                    override fun onAsrEnabledChange(enabled: Boolean) { host?.saveAsrEnabled(enabled) }
                    override fun onSpeechRateChange(rate: Float) { host?.saveSpeechRate(rate) }
                    override fun onVoiceChange(voice: String) { host?.saveVoice(voice) }
                }
            }
            val initialApiUrl = remember { host?.getApiUrl() ?: "" }
            val initialApiKey = remember { host?.getApiKey() ?: "" }
            val initialModel = remember { host?.getModel() ?: "gpt-4o-mini" }
            val initialTemperature = remember { host?.getTemperature() ?: 0.7f }
            val initialMaxTokens = remember { host?.getMaxTokens() ?: 4096 }
            val initialTtsEnabled = remember { host?.isTtsEnabled() ?: true }
            val initialAsrEnabled = remember { host?.isAsrEnabled() ?: true }
            val initialSpeechRate = remember { host?.getSpeechRate() ?: 1.0f }
            val initialVoice = remember { host?.getVoice() ?: "甜美女声" }
            val live2dModels = remember { host?.getLive2DModels() ?: emptyList() }
            val currentLive2DModelId = remember { host?.getCurrentLive2DModelId() ?: "" }
            val live2dScale = remember { host?.getLive2DScale() ?: 1f }

            SettingsScreen(
                callbacks = stableCallbacks,
                initialApiUrl = initialApiUrl,
                initialApiKey = initialApiKey,
                initialModel = initialModel,
                initialTemperature = initialTemperature,
                initialMaxTokens = initialMaxTokens,
                initialTtsEnabled = initialTtsEnabled,
                initialAsrEnabled = initialAsrEnabled,
                initialSpeechRate = initialSpeechRate,
                initialVoice = initialVoice,
                onNavigate = { route ->
                    when (route) {
                        "live2d_import", "live2d_scan" -> {
                            // 启动 ModelManagerActivity（含完整 Live2D 导入/扫描功能）
                            val ctx = navController.context
                            val intent = android.content.Intent(ctx, com.aicompanion.ui.ModelManagerActivity::class.java)
                            intent.putExtra("action", route)
                            ctx.startActivity(intent)
                        }
                        else -> navController.navigate(route) { launchSingleTop = true }
                    }
                },
                live2dModels = live2dModels,
                currentLive2DModelId = currentLive2DModelId,
                onLive2DModelChange = { modelId -> host?.setLive2DModel(modelId) },
                live2dScale = live2dScale,
                onLive2DScaleChange = { scale -> host?.setLive2DScale(scale) },
            )
        }
        composable(
            route = StradustDestinations.DIARY,
            // DIARY 是底部 5 个 tab 之一，使用 tab 切换动画（淡入+缩放）
            enterTransition = { tabEnterTransition },
            exitTransition = { tabExitTransition },
            popEnterTransition = { tabEnterTransition },
            popExitTransition = { tabExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val diaryEntries = remember(dataVersion) { host?.getDiaryEntries() ?: emptyList() }
            val diaryWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }
            DiaryScreen(
                diaryEntries = diaryEntries,
                onAddDiary = { content -> host?.addDiary(content) },
                onDiaryClick = { entry -> host?.onDiaryClick(entry.id) },
                onUpdateDiary = { id, content -> host?.updateDiary(id, content) },
                onDeleteDiary = { entry -> host?.deleteDiary(entry.date.toString()) },
                onBackClick = { navController.popBackStack() },
                onSearchClick = { navController.navigate(StradustDestinations.DIARY_SEARCH) { launchSingleTop = true } },
                wallpaperPath = diaryWallpaperPath,
            )
        }
        composable(
            route = StradustDestinations.VIRTUAL_WORLD,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            // 从 host 获取虚拟世界真实数据
            val vwState = remember(dataVersion) { host?.getVirtualWorldState() ?: VirtualWorldInfo() }
            VirtualWorldScreen(
                isSimulating = vwState.isSimulating,
                storyEvents = vwState.events,
                onToggleSimulation = { host?.toggleVirtualSimulation() },
                onBackClick = { navController.popBackStack() },
                onUploadImageClick = { navController.navigate(StradustDestinations.VW_IMAGE_UPLOAD) { launchSingleTop = true } },
            )
        }
        composable(
            route = StradustDestinations.ALBUM,
            // ALBUM 是底部 5 个 tab 之一，使用 tab 切换动画
            enterTransition = { tabEnterTransition },
            exitTransition = { tabExitTransition },
            popEnterTransition = { tabEnterTransition },
            popExitTransition = { tabExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val albumPhotos = remember(dataVersion) { host?.getAlbumPhotos() ?: emptyList() }
            val albumWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }
            AlbumScreen(
                albumPhotos = albumPhotos,
                onBackClick = { navController.popBackStack() },
                onGenImageClick = { navController.navigate(StradustDestinations.ALBUM_GEN) { launchSingleTop = true } },
                wallpaperPath = albumWallpaperPath,
            )
        }
        composable(
            route = StradustDestinations.CHECK_IN,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val checkInStreak = remember(dataVersion) { host?.getCheckInStreak() ?: 0 }
            val checkedInToday = remember(dataVersion) { host?.isCheckedInToday() ?: false }
            val checkedDates = remember(dataVersion) { host?.getCheckedDates() ?: emptySet() }
            val totalCheckIns = remember(dataVersion) { host?.getTotalCheckIns() ?: 0 }
            CheckInScreen(
                consecutiveDays = checkInStreak,
                isCheckedInToday = checkedInToday,
                checkedDates = checkedDates,
                totalCheckIns = totalCheckIns,
                onCheckIn = { host?.performCheckIn() },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = StradustDestinations.ACHIEVEMENT,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val achievements = remember(dataVersion) { host?.getAchievements() }
            AchievementScreen(
                achievementModels = achievements,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = StradustDestinations.PROFILE,
            // PROFILE 是底部 5 个 tab 之一，使用 tab 切换动画
            enterTransition = { tabEnterTransition },
            exitTransition = { tabExitTransition },
            popEnterTransition = { tabEnterTransition },
            popExitTransition = { tabExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val profileDataRaw = remember(dataVersion) { host?.getProfileData() ?: com.aicompanion.ui.profile.ProfileData() }
            val profileDaysTogether = remember(dataVersion) { host?.getDaysTogether() ?: 0 }
            // 补充 daysTogether（后端 getProfileData 未包含此字段，从 getDaysTogether 单独获取）
            val profileData = remember(profileDataRaw, profileDaysTogether) {
                profileDataRaw.copy(daysTogether = profileDaysTogether)
            }
            val profilePersonas = remember(dataVersion) { host?.getPersonas() ?: emptyList() }
            val profileAchievements = remember(dataVersion) { host?.getAchievements() ?: emptyList() }
            val profileAiAvatarPath = remember(dataVersion) { host?.getAiAvatarPath() }
            // 当前选中的角色ID（默认取第一个角色或空字符串）
            var profileSelectedPersonaId by remember { mutableStateOf(profilePersonas.firstOrNull()?.id ?: "") }
            // 稳定化回调 lambda，避免每次重组创建新实例触发 ProfileScreen 重组
            val onProfileSettingsClick: () -> Unit = remember {
                { navController.navigate(StradustDestinations.SETTINGS) { launchSingleTop = true } }
            }
            val onProfileLogoutClick = remember {
                {
                    host?.logout()
                    navController.navigate(StradustDestinations.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            val onProfileNavigate = remember {
                { route: String -> navController.navigate(route) { launchSingleTop = true } }
            }
            val versionName = remember { host?.getAppVersionName() ?: "1.0.0" }
            val profileWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }

            ProfileScreen(
                profileData = profileData,
                personas = profilePersonas,
                selectedPersonaId = profileSelectedPersonaId,
                onPersonaSelected = { personaId ->
                    profileSelectedPersonaId = personaId
                    host?.openProfile(personaId)
                },
                achievements = profileAchievements,
                aiAvatarPath = profileAiAvatarPath,
                onSettingsClick = onProfileSettingsClick,
                onMemoryPoolClick = { host?.onFeatureClick(10) },  // 记忆池
                onChatHistoryClick = { host?.onFeatureClick(14) },  // 聊天记录
                onHelpClick = {
                    // ProfileScreen 内部已实现帮助弹窗，此回调保留供外部扩展
                },
                onAboutClick = {
                    // ProfileScreen 内部已实现关于弹窗，此回调保留供外部扩展
                },
                onLogoutClick = onProfileLogoutClick,
                versionName = versionName,
                onBackClick = { navController.popBackStack() },
                onChangeAiAvatar = { host?.pickAiAvatar() },
                onChangeUserAvatar = { host?.pickUserAvatar() },
                onNavigate = onProfileNavigate,
                refreshTick = dataVersion,
                wallpaperPath = profileWallpaperPath,
            )
        }
        composable(
            route = StradustDestinations.GROUP_CHAT_LIST,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val groups = remember(dataVersion) { host?.getGroupChatList() ?: emptyList() }
            val groupChatListWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }
            GroupChatListScreen(
                groups = groups,
                onBackClick = { navController.popBackStack() },
                onGroupClick = { groupId ->
                    // 导航到群聊聊天页面（Compose 版本）
                    navController.navigate(StradustDestinations.GROUP_CHAT.replace("{groupId}", groupId)) {
                        launchSingleTop = true
                    }
                },
                onCreateGroup = { name ->
                    // 调用 AppHost 创建群聊，成功后导航到群聊聊天页面
                    val newId = host?.createGroup(name, emptyList())
                    if (newId != null) {
                        navController.navigate(StradustDestinations.GROUP_CHAT.replace("{groupId}", newId)) {
                            launchSingleTop = true
                        }
                    }
                },
                onDeleteGroup = { groupId ->
                    host?.deleteGroup(groupId)
                },
                wallpaperPath = groupChatListWallpaperPath,
            )
        }
        composable(
            route = StradustDestinations.GROUP_CHAT,
            arguments = listOf(
                androidx.navigation.navArgument("groupId") { type = NavType.StringType },
            ),
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) { backStackEntry ->
            // 在 composable 内部读取 dataVersion，仅触发本页重组
            val dataVersion = host?.dataVersionState?.value ?: 0
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            // 从群聊列表中查找群名称
            val groupList = remember(dataVersion) { host?.getGroupChatList() ?: emptyList() }
            val groupInfo = groupList.find { it.id == groupId }
            // 通过 AppHost 解析成员名称（解决硬编码问题）
            val memberNames = remember(dataVersion, groupId) { host?.getGroupMemberNames(groupId) ?: emptyList() }
            val memberPersonaIds = remember(dataVersion, groupId) { host?.getGroupMemberPersonaIds(groupId) ?: emptyList() }
            val groupSpeakMode = remember(dataVersion, groupId) { host?.getGroupSpeakMode(groupId) ?: "round_robin" }
            val manualSelected = remember(dataVersion, groupId) { host?.getManualSelectedIds(groupId) ?: emptySet() }
            val groupChatWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }

            GroupChatScreen(
                groupId = groupId,
                groupName = groupInfo?.name ?: "未命名群",
                messages = remember(dataVersion, groupId) { host?.getGroupMessages(groupId) ?: emptyList() },
                memberNames = memberNames,
                isTyping = host?.getGroupTypingState(groupId)?.value ?: false,
                onSendMessage = { text -> host?.sendGroupMessage(groupId, text) },
                onStartVoice = { host?.startVoice() },
                onStopVoice = { host?.stopVoice() },
                isRecording = host?.isVoiceRecording() ?: false,
                onBackClick = { navController.popBackStack() },
                onSettingsClick = { navController.navigate("group_chat_settings/$groupId") { launchSingleTop = true } },
                wallpaperPath = groupChatWallpaperPath,
                speakMode = groupSpeakMode,
                onSpeakModeChange = { mode -> host?.setGroupSpeakMode(groupId, mode) },
                memberPersonaIds = memberPersonaIds,
                manualSelectedIds = manualSelected,
                onManualSelectionChange = { ids -> host?.setManualSelectedIds(groupId, ids) },
            )
        }
        composable(
            route = StradustDestinations.PHONE_CALL,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            PhoneCallScreen(
                personaName = host?.getCallPersonaName() ?: "星尘",
                avatarUrl = host?.getAiAvatarPath(),
                isCallActive = host?.isCallActive() ?: false,
                callStatus = host?.getCallStatus() ?: "正在接听...",
                callDurationMs = host?.getCallDurationMs() ?: 0L,
                transcript = host?.getCallTranscript() ?: "",
                isMuted = host?.isCallMuted() ?: false,
                isSpeakerOn = host?.isCallSpeakerOn() ?: true,
                waveformMode = host?.getCallWaveformMode() ?: 0,
                onHangUp = { host?.hangUp() },
                onToggleMute = { host?.toggleCallMute() },
                onToggleSpeaker = { host?.toggleCallSpeaker() },
            )
        }

        // ===== 新增页面路由 =====

        // 时光胶囊
        composable(
            route = StradustDestinations.TIME_CAPSULE,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val context = LocalContext.current
            val personaId = remember {
                val pm = com.aicompanion.persona.PersonaManager(context)
                pm.load()
                pm.getActivePersona()?.id ?: "default"
            }
            val manager = remember(personaId) { TimeCapsuleManager(context, personaId) }
            var refreshTick by remember { mutableStateOf(0) }
            val capsules = remember(refreshTick) { manager.loadCapsules() }
            TimeCapsuleScreen(
                capsules = capsules,
                onCreateCapsule = { title, content, openDate ->
                    manager.createCapsule(title, content, openDate)
                    refreshTick++
                },
                onOpenCapsule = { capsule ->
                    manager.markOpened(capsule.id)
                    refreshTick++
                },
                onDeleteCapsule = { capsule ->
                    manager.deleteCapsule(capsule.id)
                    refreshTick++
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        // 里程碑与纪念日
        composable(
            route = StradustDestinations.MILESTONE,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val context = LocalContext.current
            val personaId = remember {
                val pm = com.aicompanion.persona.PersonaManager(context)
                pm.load()
                pm.getActivePersona()?.id ?: "default"
            }
            val manager = remember(personaId) { MilestoneManager(context, personaId) }
            var refreshTick by remember { mutableStateOf(0) }
            val milestones = remember(refreshTick) { manager.loadMilestones() }
            val anniversaryMessages = remember(refreshTick) { manager.getAnniversaryMessages() }
            MilestoneScreen(
                milestones = milestones,
                anniversaryMessages = anniversaryMessages,
                onAddMilestone = { id, title, description, category ->
                    manager.recordMilestone(id, title, description, category)
                    refreshTick++
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        // 唤醒任务管理
        composable(
            route = StradustDestinations.WAKEUP_TASK,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val context = LocalContext.current
            val manager = remember { WakeUpTaskManager(context).apply { load() } }
            var refreshTick by remember { mutableStateOf(0) }
            val tasks = remember(refreshTick) { manager.getAllTasks() }
            WakeUpTaskScreen(
                tasks = tasks,
                onAddTask = { task ->
                    manager.addTask(task)
                    refreshTick++
                },
                onUpdateTask = { id, updater ->
                    manager.updateTask(id, updater)
                    refreshTick++
                },
                onDeleteTask = { id ->
                    manager.deleteTask(id)
                    refreshTick++
                },
                onToggleTask = { id, enabled ->
                    manager.toggleTask(id, enabled)
                    refreshTick++
                },
                onBackClick = { navController.popBackStack() },
            )
        }

        // 成长阶段
        composable(
            route = StradustDestinations.GROWTH,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val context = LocalContext.current
            val personaMgr = remember { PersonaManager(context) }
            val activePersona = remember { personaMgr.getActivePersona() }
            val affectionMgr = remember(activePersona.id) { AffectionManager(context, activePersona.id) }
            val affectionLevel = remember { affectionMgr.affectionLevel }
            val daysSinceFirstUse = remember { affectionMgr.getDaysSinceFirstUse() }
            GrowthScreen(
                affectionLevel = affectionLevel,
                daysSinceFirstUse = daysSinceFirstUse,
                onBackClick = { navController.popBackStack() },
            )
        }

        // 角色统计（默认角色）
        composable(
            route = StradustDestinations.PERSONA_STATS,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val context = LocalContext.current
            val personaMgr = remember { PersonaManager(context) }
            val activePersona = remember { personaMgr.getActivePersona() }
            PersonaStatsScreen(
                personaId = activePersona.id,
                onBackClick = { navController.popBackStack() },
            )
        }

        // 角色统计（指定角色 ID）
        composable(
            route = StradustDestinations.PERSONA_STATS_WITH_ID,
            arguments = listOf(
                androidx.navigation.navArgument("personaId") { type = NavType.StringType },
            ),
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) { backStackEntry ->
            val personaId = backStackEntry.arguments?.getString("personaId") ?: "default"
            PersonaStatsScreen(
                personaId = personaId,
                onBackClick = { navController.popBackStack() },
            )
        }

        // 收藏列表（默认角色）
        composable(
            route = StradustDestinations.FAVORITES,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val context = LocalContext.current
            val personaMgr = remember { PersonaManager(context) }
            val activePersona = remember { personaMgr.getActivePersona() }
            FavoritesScreen(
                personaId = activePersona.id,
                onBackClick = { navController.popBackStack() },
            )
        }

        // 收藏列表（指定角色 ID）
        composable(
            route = StradustDestinations.FAVORITES_WITH_ID,
            arguments = listOf(
                androidx.navigation.navArgument("personaId") { type = NavType.StringType },
            ),
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) { backStackEntry ->
            val personaId = backStackEntry.arguments?.getString("personaId") ?: "default"
            FavoritesScreen(
                personaId = personaId,
                onBackClick = { navController.popBackStack() },
            )
        }

        // 日程管理
        composable(
            route = StradustDestinations.SCHEDULE,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            ScheduleScreen(onBackClick = { navController.popBackStack() })
        }

        // 难忘时刻
        composable(
            route = StradustDestinations.MEMORABLE_MOMENTS,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            MemorableMomentsScreen(onBackClick = { navController.popBackStack() })
        }

        // 日记搜索
        composable(
            route = StradustDestinations.DIARY_SEARCH,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            DiarySearchScreen(onBackClick = { navController.popBackStack() })
        }

        // 贴纸管理
        composable(
            route = StradustDestinations.STICKER_MANAGER,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            StickerManagerScreen(onBackClick = { navController.popBackStack() })
        }

        // 相册图片生成
        composable(
            route = StradustDestinations.ALBUM_GEN,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            AlbumGenScreen(onBackClick = { navController.popBackStack() })
        }

        // 群聊设置
        composable(
            route = StradustDestinations.GROUP_CHAT_SETTINGS,
            arguments = listOf(
                androidx.navigation.navArgument("groupId") { type = NavType.StringType },
            ),
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupChatSettingsScreen(
                groupId = groupId,
                onBackClick = { navController.popBackStack() },
            )
        }

        // 虚拟世界参考图上传
        composable(
            route = StradustDestinations.VW_IMAGE_UPLOAD,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            VwImageUploadScreen(onBackClick = { navController.popBackStack() })
        }

        // 日历与生理期
        composable(
            route = StradustDestinations.CALENDAR,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            val dataVersion = host?.dataVersionState?.value ?: 0
            val calendarWallpaperPath = remember(dataVersion) { host?.getWallpaperPath() }
            CalendarScreen(
                onBackClick = { navController.popBackStack() },
                wallpaperPath = calendarWallpaperPath,
            )
        }

        // 昵称管理
        composable(
            route = StradustDestinations.NICKNAME,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            NicknameScreen(onBackClick = { navController.popBackStack() })
        }

        // iLink 绑定
        composable(
            route = StradustDestinations.ILINK,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            IlinkScreen(onBackClick = { navController.popBackStack() })
        }

        // 本地模型管理
        composable(
            route = StradustDestinations.LOCAL_MODEL,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            LocalModelScreen(onBackClick = { navController.popBackStack() })
        }

        // 记忆池
        composable(
            route = StradustDestinations.MEMORY_POOL,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            MemoryPoolScreen(onBackClick = { navController.popBackStack() })
        }

        // 角色设定
        composable(
            route = StradustDestinations.CHARACTER_CARD,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            CharacterCardScreen(
                onBackClick = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
            )
        }

        // 角色设定 - 直接进入创建模式
        composable(
            route = StradustDestinations.CHARACTER_CARD_CREATE,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            CharacterCardScreen(
                onBackClick = { navController.popBackStack() },
                onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } },
                startInCreateMode = true,
            )
        }

        // 世界书（独立页面）
        composable(
            route = StradustDestinations.WORLD_BOOK,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            WorldBookScreen(
                onBackClick = { navController.popBackStack() },
            )
        }

        // 像素宠物
        composable(
            route = StradustDestinations.PIXEL_PET,
            enterTransition = { pageEnterTransition },
            exitTransition = { pageExitTransition },
            popEnterTransition = { pageEnterTransition },
            popExitTransition = { pageExitTransition },
        ) {
            PixelPetScreen(onBackClick = { navController.popBackStack() })
        }
    }
        } // content Box 闭合

        // ===== bottomBar 层：叠加在窗口底部，不参与键盘避让 =====
        // 键盘未弹出时正常显示；键盘弹出时留在原位被键盘自然遮挡。
        if (showBottomBar) {
            StradustBottomBar(
                items = bottomNavItems,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    // 如果点击的是当前已选中的 tab，不重复压栈；
                    // 否则导航到目标页面并 pop 到根，避免栈堆积
                    if (currentRoute != route) {
                        // 首页按钮：用 popBackStack 直接弹回 home（更可靠）
                        if (route == StradustDestinations.HOME) {
                            val popped = navController.popBackStack(
                                StradustDestinations.HOME, inclusive = false
                            )
                            if (!popped) {
                                // home 不在栈中，直接导航
                                navController.navigate(StradustDestinations.HOME) {
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    } // 外层 Box 闭合
}