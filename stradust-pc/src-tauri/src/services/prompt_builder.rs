// 提示词构建：身份块+记忆注入+世界书+RAG检索，对应原prompt/PromptBuilder.kt

use std::sync::Arc;

use crate::db::database::Database;
use crate::models::persona::Persona;
use crate::models::settings::ProviderProfile;
use crate::services::memory_service::MemoryService;
use crate::services::rag_service::RagService;
use crate::utils::helpers;

/// 提示词构建器
/// 持有 Arc<MemoryService> 和 Arc<RagService>，与 AppState 共享同一实例
pub struct PromptBuilder {
    memory_service: Arc<MemoryService>,
    rag_service: Arc<RagService>,
}

impl PromptBuilder {
    pub fn new(memory_service: Arc<MemoryService>, rag_service: Arc<RagService>) -> Self {
        PromptBuilder { memory_service, rag_service }
    }

    /// 构建身份块
    pub fn build_identity_block(persona: &Persona) -> String {
        let mut block = String::new();

        block.push_str(&format!("你是「{}」，一个AI伙伴。\n", persona.name));

        if !persona.system_prompt.is_empty() {
            block.push_str(&format!("\n【系统设定】\n{}\n", persona.system_prompt));
        }

        if !persona.personality.is_empty() {
            block.push_str(&format!("\n【性格特征】\n{}\n", persona.personality));
        }

        if !persona.speaking_style.is_empty() {
            block.push_str(&format!("\n【说话风格】\n{}\n", persona.speaking_style));
        }

        if let Some(story) = &persona.background_story {
            if !story.is_empty() {
                block.push_str(&format!("\n【背景故事】\n{}\n", story));
            }
        }

        block
    }

    /// 构建记忆块
    pub fn build_memory_block(&self, db: &Database, persona_id: &str, session_id: &str) -> String {
        let pool_block = self.memory_service.get_pool_block(db, persona_id, session_id);

        let mut block = String::new();

        if !pool_block.core_block.is_empty() {
            block.push_str(&pool_block.core_block);
            block.push('\n');
        }

        if !pool_block.detail_block.is_empty() {
            block.push_str(&pool_block.detail_block);
        }

        block
    }

    /// 构建世界书块
    pub fn build_world_lore_block(persona: &Persona) -> String {
        match &persona.world_lore {
            Some(lore) if !lore.is_empty() => format!("【世界设定】\n{}\n", lore),
            _ => String::new(),
        }
    }

    /// 构建RAG检索块
    pub fn build_rag_block(&self, db: &Database, persona_id: &str, query: &str) -> String {
        let results = self.rag_service.search(db, persona_id, query, 5);

        match results {
            Ok(chunks) if !chunks.is_empty() => {
                let items: Vec<String> = chunks.iter()
                    .map(|c| format!("- {}", c.content))
                    .collect();
                format!("【相关知识】\n{}\n", items.join("\n"))
            }
            _ => String::new(),
        }
    }

    /// 构建完整提示词
    pub fn build_full_prompt(
        &self,
        db: &Database,
        persona: &Persona,
        session_id: &str,
        user_message: &str,
    ) -> Vec<crate::models::chat::ApiMessage> {
        let mut system_content = String::new();

        // 身份块
        system_content.push_str(&Self::build_identity_block(persona));

        // 世界书块
        let lore_block = Self::build_world_lore_block(persona);
        if !lore_block.is_empty() {
            system_content.push_str(&lore_block);
        }

        // 记忆块
        let memory_block = self.build_memory_block(db, &persona.id, session_id);
        if !memory_block.is_empty() {
            system_content.push('\n');
            system_content.push_str(&memory_block);
        }

        // RAG检索块
        let rag_block = self.build_rag_block(db, &persona.id, user_message);
        if !rag_block.is_empty() {
            system_content.push('\n');
            system_content.push_str(&rag_block);
        }

        // 行为指导
        system_content.push_str("\n【行为指导】\n");
        system_content.push_str("- 请用自然、生动的方式回复，保持角色一致性\n");
        system_content.push_str("- 如果有情绪变化，请在回复末尾用 [[emotion:xxx]] 标注\n");
        system_content.push_str("- 如果需要执行动作，请在回复末尾用 [[action:xxx]] 标注\n");
        system_content.push_str("- 情绪可选: happy, sad, angry, tsundere, shy, excited, calm, worried, surprised, neutral\n");
        system_content.push_str("- 动作可选: idle, nod, shake_head, wave, think, smile, hug, clap, tsundere\n");

        vec![
            crate::models::chat::ApiMessage {
                role: "system".to_string(),
                content: Some(system_content),
                tool_calls: None,
                tool_call_id: None,
                name: None,
            },
        ]
    }

    /// 构建群聊提示词
    pub fn build_group_chat_prompt(
        &self,
        db: &Database,
        personas: &[Persona],
        group_name: &str,
    ) -> Vec<crate::models::chat::ApiMessage> {
        let mut system_content = format!("你正在参与一个名为「{}」的群聊。\n\n", group_name);

        system_content.push_str("【群聊成员】\n");
        for persona in personas {
            system_content.push_str(&format!("- {}: {}\n", persona.name, persona.personality));
        }

        system_content.push_str("\n【规则】\n");
        system_content.push_str("- 每次回复时，请以某个角色的视角发言\n");
        system_content.push_str("- 在回复开头标注角色名，格式: 【角色名】内容\n");
        system_content.push_str("- 角色之间可以互动和对话\n");

        vec![
            crate::models::chat::ApiMessage {
                role: "system".to_string(),
                content: Some(system_content),
                tool_calls: None,
                tool_call_id: None,
                name: None,
            },
        ]
    }

    /// 构建自动世界推演提示词
    pub fn build_auto_world_lore_prompt(
        &self,
        world_description: &str,
        current_situation: &str,
        characters: &str,
    ) -> String {
        format!(
            "你是一个世界推演引擎。根据当前世界状态，生成下一个时间节点的故事发展。\n\n\
             【世界设定】\n{}\n\n\
             【当前状况】\n{}\n\n\
             【角色状态】\n{}\n\n\
             请生成下一个事件，包含：\n\
             1. 事件标题\n\
             2. 事件描述\n\
             3. 参与角色\n\
             4. 对世界的影响\n\
             5. 角色的情绪变化",
            world_description, current_situation, characters
        )
    }
}
