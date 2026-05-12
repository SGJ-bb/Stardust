#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI桌宠 - 桌面版测试程序
直接在电脑上运行，无需Android环境
"""

import sys
import json
import random
import requests
from datetime import datetime
from PyQt5.QtWidgets import (
    QApplication, QMainWindow, QWidget, QVBoxLayout, QHBoxLayout,
    QTextEdit, QLineEdit, QPushButton, QLabel, QComboBox,
    QCheckBox, QSlider, QTabWidget, QScrollArea, QFrame,
    QMessageBox, QFileDialog
)
from PyQt5.QtCore import Qt, QThread, pyqtSignal, QTimer
from PyQt5.QtGui import QFont, QPixmap, QMovie


class ChatWorker(QThread):
    """后台聊天线程"""
    finished = pyqtSignal(dict)
    error = pyqtSignal(str)
    
    def __init__(self, api_url, user_id, message, app_category=None, offline_mode=False):
        super().__init__()
        self.api_url = api_url
        self.user_id = user_id
        self.message = message
        self.app_category = app_category
        self.offline_mode = offline_mode
    
    def run(self):
        try:
            response = requests.post(
                f"{self.api_url}/api/v1/chat/send",
                json={
                    "user_id": self.user_id,
                    "message": self.message,
                    "app_category": self.app_category,
                    "is_offline_mode": self.offline_mode
                },
                timeout=10
            )
            response.raise_for_status()
            self.finished.emit(response.json())
        except Exception as e:
            self.error.emit(str(e))


class Live2DWidget(QWidget):
    """Live2D角色展示组件"""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.emotion = "neutral"
        self.action = "idle"
        self.init_ui()
        
        # 模拟动画定时器
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.update_animation)
        self.timer.start(100)
        self.frame = 0
    
    def init_ui(self):
        layout = QVBoxLayout(self)
        layout.setAlignment(Qt.AlignCenter)
        
        # 角色标签（用emoji代替，后续可替换为真实Live2D）
        self.character_label = QLabel("🐱")
        self.character_label.setFont(QFont("Segoe UI Emoji", 120))
        self.character_label.setAlignment(Qt.AlignCenter)
        self.character_label.setMinimumSize(300, 300)
        layout.addWidget(self.character_label)
        
        # 状态标签
        status_layout = QHBoxLayout()
        self.emotion_label = QLabel("平静")
        self.emotion_label.setStyleSheet("""
            QLabel {
                background: #667eea;
                color: white;
                padding: 6px 12px;
                border-radius: 12px;
                font-size: 12px;
                font-weight: bold;
            }
        """)
        self.action_label = QLabel("待机")
        self.action_label.setStyleSheet("""
            QLabel {
                background: #764ba2;
                color: white;
                padding: 6px 12px;
                border-radius: 12px;
                font-size: 12px;
                font-weight: bold;
            }
        """)
        status_layout.addStretch()
        status_layout.addWidget(self.emotion_label)
        status_layout.addWidget(self.action_label)
        status_layout.addStretch()
        layout.addLayout(status_layout)
    
    def set_emotion(self, emotion):
        self.emotion = emotion
        emojis = {
            "happy": "😺", "angry": "😾", "sad": "😿",
            "surprised": "🙀", "tsundere": "😼", "neutral": "🐱"
        }
        names = {
            "happy": "开心", "angry": "生气", "sad": "伤心",
            "surprised": "惊讶", "tsundere": "傲娇", "neutral": "平静"
        }
        self.character_label.setText(emojis.get(emotion, "🐱"))
        self.emotion_label.setText(names.get(emotion, emotion))
        
        # 添加简单的缩放动画效果
        self.character_label.setStyleSheet("QLabel { transform: scale(1.2); }")
        QTimer.singleShot(200, lambda: self.character_label.setStyleSheet(""))
    
    def set_action(self, action):
        self.action = action
        names = {
            "tail_flick": "摇尾巴", "ear_twitch": "抖耳朵",
            "blush": "脸红", "stretch": "伸懒腰",
            "yawn": "打哈欠", "idle": "待机"
        }
        self.action_label.setText(names.get(action, action))
    
    def update_animation(self):
        self.frame += 1
        # 简单的浮动效果
        offset = int(5 * (1 if self.frame % 20 < 10 else -1))
        self.character_label.move(
            self.character_label.x(),
            self.character_label.y() + offset
        )


class ChatWidget(QWidget):
    """聊天组件"""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.parent_window = parent
        self.init_ui()
    
    def init_ui(self):
        layout = QVBoxLayout(self)
        
        # 聊天显示区
        self.chat_display = QTextEdit()
        self.chat_display.setReadOnly(True)
        self.chat_display.setStyleSheet("""
            QTextEdit {
                background: #f5f5f5;
                border: none;
                border-radius: 12px;
                padding: 12px;
                font-size: 14px;
            }
        """)
        layout.addWidget(self.chat_display)
        
        # 输入区
        input_layout = QHBoxLayout()
        self.message_input = QLineEdit()
        self.message_input.setPlaceholderText("输入消息...")
        self.message_input.setStyleSheet("""
            QLineEdit {
                padding: 12px 16px;
                border: 2px solid #e0e0e0;
                border-radius: 24px;
                font-size: 14px;
            }
            QLineEdit:focus {
                border-color: #667eea;
            }
        """)
        self.message_input.returnPressed.connect(self.send_message)
        
        self.send_btn = QPushButton("发送")
        self.send_btn.setStyleSheet("""
            QPushButton {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 #667eea, stop:1 #764ba2);
                color: white;
                border: none;
                border-radius: 24px;
                padding: 12px 24px;
                font-size: 14px;
                font-weight: bold;
            }
            QPushButton:hover {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 #5a6fd6, stop:1 #6a4190);
            }
            QPushButton:disabled {
                background: #cccccc;
            }
        """)
        self.send_btn.clicked.connect(self.send_message)
        
        input_layout.addWidget(self.message_input)
        input_layout.addWidget(self.send_btn)
        layout.addLayout(input_layout)
        
        # 添加欢迎消息
        self.add_message("星尘", "主人好喵！我是星尘，今天想聊什么？", False, "happy", "idle")
    
    def add_message(self, sender, text, is_user, emotion=None, action=None):
        time_str = datetime.now().strftime("%H:%M")
        
        if is_user:
            html = f"""
            <div style="text-align: right; margin: 8px 0;">
                <div style="display: inline-block; background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
                    stop:0 #667eea, stop:1 #764ba2); color: white; padding: 10px 14px;
                    border-radius: 16px; border-bottom-right-radius: 4px; max-width: 80%;">
                    {text}
                </div>
                <div style="font-size: 10px; color: #999; margin-top: 4px;">{time_str}</div>
            </div>
            """
        else:
            emotion_emoji = {"happy": "😺", "angry": "😾", "sad": "😿",
                           "surprised": "🙀", "tsundere": "😼", "neutral": ""}.get(emotion, "")
            html = f"""
            <div style="text-align: left; margin: 8px 0;">
                <div style="font-size: 12px; color: #667eea; margin-bottom: 4px;">
                    {sender} {emotion_emoji}
                </div>
                <div style="display: inline-block; background: white; color: #333;
                    padding: 10px 14px; border-radius: 16px; border-bottom-left-radius: 4px;
                    max-width: 80%; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                    {text}
                </div>
                <div style="font-size: 10px; color: #999; margin-top: 4px;">{time_str}</div>
            </div>
            """
        
        self.chat_display.append(html)
        
        # 更新角色状态
        if emotion and self.parent_window:
            self.parent_window.live2d_widget.set_emotion(emotion)
        if action and self.parent_window:
            self.parent_window.live2d_widget.set_action(action)
    
    def send_message(self):
        text = self.message_input.text().strip()
        if not text:
            return
        
        self.message_input.clear()
        self.add_message("你", text, True)
        
        # 禁用发送按钮
        self.send_btn.setEnabled(False)
        self.send_btn.setText("发送中...")
        
        # 获取设置
        api_url = self.parent_window.settings_widget.api_url_input.text() if self.parent_window else "http://localhost:8001"
        user_id = self.parent_window.settings_widget.user_id_input.text() if self.parent_window else "desktop_user"
        offline_mode = self.parent_window.settings_widget.offline_check.isChecked() if self.parent_window else False
        
        # 创建后台线程
        self.worker = ChatWorker(api_url, user_id, text, offline_mode=offline_mode)
        self.worker.finished.connect(self.on_response)
        self.worker.error.connect(self.on_error)
        self.worker.start()
    
    def on_response(self, data):
        self.send_btn.setEnabled(True)
        self.send_btn.setText("发送")
        
        text = data.get("text", "...")
        emotion = data.get("emotion", "neutral")
        action = data.get("action", "idle")
        
        self.add_message("星尘", text, False, emotion, action)
    
    def on_error(self, error_msg):
        self.send_btn.setEnabled(True)
        self.send_btn.setText("发送")
        
        # 离线模式或网络错误时使用本地回复
        offline_responses = [
            ("网络好像有点问题喵，稍后再试~", "sad", "ear_twitch"),
            ("我卡住了喵...", "sad", "idle"),
            ("连接不上服务器呢，检查下网络？", "neutral", "ear_twitch"),
        ]
        response = random.choice(offline_responses)
        self.add_message("星尘", response[0], False, response[1], response[2])


class SettingsWidget(QWidget):
    """设置面板"""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.init_ui()
    
    def init_ui(self):
        layout = QVBoxLayout(self)
        layout.setAlignment(Qt.AlignTop)
        
        # 服务配置
        config_frame = self.create_section("服务配置")
        config_layout = QVBoxLayout(config_frame)
        
        config_layout.addWidget(QLabel("后端服务器地址:"))
        self.api_url_input = QLineEdit("http://localhost:8001")
        self.api_url_input.setStyleSheet(self.input_style())
        config_layout.addWidget(self.api_url_input)
        
        config_layout.addWidget(QLabel("用户ID:"))
        self.user_id_input = QLineEdit("desktop_user")
        self.user_id_input.setStyleSheet(self.input_style())
        config_layout.addWidget(self.user_id_input)
        
        layout.addWidget(config_frame)
        
        # 功能开关
        switches_frame = self.create_section("功能开关")
        switches_layout = QVBoxLayout(switches_frame)
        
        self.offline_check = QCheckBox("离线模式（使用本地回复）")
        self.offline_check.setStyleSheet("font-size: 13px;")
        switches_layout.addWidget(self.offline_check)
        
        self.tts_check = QCheckBox("语音合成（TTS）")
        self.tts_check.setChecked(True)
        self.tts_check.setStyleSheet("font-size: 13px;")
        switches_layout.addWidget(self.tts_check)
        
        layout.addWidget(switches_frame)
        
        # 交互设置
        interaction_frame = self.create_section("交互设置")
        interaction_layout = QVBoxLayout(interaction_frame)
        
        interaction_layout.addWidget(QLabel("语言风格:"))
        self.style_combo = QComboBox()
        self.style_combo.addItems(["正常（口语化）", "毒舌傲娇", "可爱卖萌"])
        self.style_combo.setStyleSheet(self.combo_style())
        interaction_layout.addWidget(self.style_combo)
        
        interaction_layout.addWidget(QLabel("唠叨频率:"))
        self.nag_slider = QSlider(Qt.Horizontal)
        self.nag_slider.setMinimum(0)
        self.nag_slider.setMaximum(3)
        self.nag_slider.setValue(2)
        self.nag_slider.setTickPosition(QSlider.TicksBelow)
        interaction_layout.addWidget(self.nag_slider)
        
        nag_labels = QHBoxLayout()
        for label in ["关闭", "低", "中", "高"]:
            lbl = QLabel(label)
            lbl.setStyleSheet("font-size: 11px; color: #999;")
            nag_labels.addWidget(lbl)
        interaction_layout.addLayout(nag_labels)
        
        layout.addWidget(interaction_frame)
        
        # Live2D模型
        model_frame = self.create_section("Live2D模型")
        model_layout = QVBoxLayout(model_frame)
        
        self.model_path_btn = QPushButton("选择模型文件夹")
        self.model_path_btn.setStyleSheet("""
            QPushButton {
                background: #f0f0f0;
                border: 2px dashed #ccc;
                border-radius: 8px;
                padding: 20px;
                font-size: 13px;
            }
            QPushButton:hover {
                border-color: #667eea;
                background: #f0f0ff;
            }
        """)
        self.model_path_btn.clicked.connect(self.select_model_folder)
        model_layout.addWidget(self.model_path_btn)
        
        self.model_info = QLabel("未选择模型")
        self.model_info.setStyleSheet("font-size: 12px; color: #999;")
        model_layout.addWidget(self.model_info)
        
        layout.addWidget(model_frame)
        
        layout.addStretch()
    
    def create_section(self, title):
        frame = QFrame()
        frame.setStyleSheet("""
            QFrame {
                background: white;
                border-radius: 12px;
                padding: 8px;
            }
        """)
        layout = QVBoxLayout(frame)
        layout.setContentsMargins(16, 16, 16, 16)
        
        title_label = QLabel(title)
        title_label.setStyleSheet("""
            QLabel {
                font-size: 14px;
                font-weight: bold;
                color: #333;
                padding-bottom: 8px;
                border-bottom: 2px solid #667eea;
            }
        """)
        layout.addWidget(title_label)
        
        return frame
    
    def input_style(self):
        return """
            QLineEdit {
                padding: 10px 12px;
                border: 1px solid #e0e0e0;
                border-radius: 8px;
                font-size: 13px;
            }
            QLineEdit:focus {
                border-color: #667eea;
            }
        """
    
    def combo_style(self):
        return """
            QComboBox {
                padding: 10px 12px;
                border: 1px solid #e0e0e0;
                border-radius: 8px;
                font-size: 13px;
            }
            QComboBox:focus {
                border-color: #667eea;
            }
        """
    
    def select_model_folder(self):
        folder = QFileDialog.getExistingDirectory(self, "选择Live2D模型文件夹")
        if folder:
            self.model_info.setText(f"已选择: {folder}")
            self.model_path_btn.setText("更换模型文件夹")


class MemoryWidget(QWidget):
    """记忆管理组件"""
    def __init__(self, parent=None):
        super().__init__(parent)
        self.init_ui()
    
    def init_ui(self):
        layout = QVBoxLayout(self)
        
        # 标题
        title = QLabel("记忆管理")
        title.setStyleSheet("font-size: 18px; font-weight: bold; color: #333;")
        layout.addWidget(title)
        
        # 说明
        desc = QLabel("查看和管理AI记住的事情")
        desc.setStyleSheet("font-size: 12px; color: #999; margin-bottom: 12px;")
        layout.addWidget(desc)
        
        # 记忆列表
        self.memory_list = QTextEdit()
        self.memory_list.setReadOnly(True)
        self.memory_list.setStyleSheet("""
            QTextEdit {
                background: #f8f9fa;
                border: none;
                border-radius: 12px;
                padding: 12px;
            }
        """)
        self.memory_list.setText("暂无记忆记录\n\n与AI对话后，重要的信息会被记录在这里。")
        layout.addWidget(self.memory_list)
        
        # 操作按钮
        btn_layout = QHBoxLayout()
        
        self.refresh_btn = QPushButton("刷新记忆")
        self.refresh_btn.setStyleSheet(self.btn_style("#667eea"))
        self.refresh_btn.clicked.connect(self.load_memories)
        
        self.clear_btn = QPushButton("清空记忆")
        self.clear_btn.setStyleSheet(self.btn_style("#f44336"))
        self.clear_btn.clicked.connect(self.clear_memories)
        
        btn_layout.addWidget(self.refresh_btn)
        btn_layout.addWidget(self.clear_btn)
        layout.addLayout(btn_layout)
    
    def btn_style(self, color):
        return f"""
            QPushButton {{
                background: {color};
                color: white;
                border: none;
                border-radius: 8px;
                padding: 10px 20px;
                font-size: 13px;
                font-weight: bold;
            }}
            QPushButton:hover {{
                background: {color}dd;
            }}
        """
    
    def load_memories(self):
        # 模拟加载记忆
        sample_memories = [
            "用户叫小明",
            "小明喜欢写代码",
            "昨天小明写代码几乎崩溃，被星尘安慰了",
            "小明经常刷短视频",
            "小恶魔模型路径: E:/小恶魔/vtuber/"
        ]
        
        html = "<h3>记忆列表</h3>"
        for i, mem in enumerate(sample_memories, 1):
            html += f"""
            <div style="background: white; padding: 10px; margin: 6px 0;
                border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1);">
                <span style="color: #667eea; font-weight: bold;">#{i}</span>
                <span style="margin-left: 8px;">{mem}</span>
            </div>
            """
        
        self.memory_list.setHtml(html)
    
    def clear_memories(self):
        reply = QMessageBox.question(
            self, "确认删除", "确定要清空所有记忆吗？",
            QMessageBox.Yes | QMessageBox.No
        )
        if reply == QMessageBox.Yes:
            self.memory_list.setText("记忆已清空\n\n与AI对话后，新的记忆会被记录在这里。")


class MainWindow(QMainWindow):
    """主窗口"""
    def __init__(self):
        super().__init__()
        self.setWindowTitle("AI桌宠 - 桌面版测试")
        self.setMinimumSize(1000, 700)
        self.init_ui()
    
    def init_ui(self):
        # 中央部件
        central_widget = QWidget()
        self.setCentralWidget(central_widget)
        layout = QHBoxLayout(central_widget)
        layout.setSpacing(20)
        layout.setContentsMargins(20, 20, 20, 20)
        
        # 左侧：Live2D角色
        left_panel = QWidget()
        left_layout = QVBoxLayout(left_panel)
        left_layout.setAlignment(Qt.AlignTop)
        
        self.live2d_widget = Live2DWidget()
        left_layout.addWidget(self.live2d_widget)
        
        # 快捷测试按钮
        test_frame = QFrame()
        test_frame.setStyleSheet("""
            QFrame {
                background: white;
                border-radius: 12px;
                padding: 8px;
            }
        """)
        test_layout = QVBoxLayout(test_frame)
        
        test_title = QLabel("快捷测试")
        test_title.setStyleSheet("font-size: 14px; font-weight: bold; color: #333;")
        test_layout.addWidget(test_title)
        
        emotions = [
            ("😺 开心", "happy"), ("😾 生气", "angry"),
            ("😿 伤心", "sad"), ("🙀 惊讶", "surprised"),
            ("😼 傲娇", "tsundere")
        ]
        
        for text, emotion in emotions:
            btn = QPushButton(text)
            btn.setStyleSheet("""
                QPushButton {
                    background: #f0f0f0;
                    border: none;
                    border-radius: 8px;
                    padding: 8px;
                    font-size: 13px;
                    text-align: left;
                }
                QPushButton:hover {
                    background: #e0e0ff;
                }
            """)
            btn.clicked.connect(lambda checked, e=emotion: self.live2d_widget.set_emotion(e))
            test_layout.addWidget(btn)
        
        left_layout.addWidget(test_frame)
        left_layout.addStretch()
        
        layout.addWidget(left_panel, 1)
        
        # 右侧：标签页
        right_panel = QTabWidget()
        right_panel.setStyleSheet("""
            QTabWidget::pane {
                border: none;
                background: transparent;
            }
            QTabBar::tab {
                background: #f0f0f0;
                border: none;
                border-radius: 8px 8px 0 0;
                padding: 10px 20px;
                font-size: 13px;
            }
            QTabBar::tab:selected {
                background: white;
                font-weight: bold;
            }
        """)
        
        # 聊天页
        self.chat_widget = ChatWidget(self)
        right_panel.addTab(self.chat_widget, "💬 聊天")
        
        # 设置页
        self.settings_widget = SettingsWidget()
        right_panel.addTab(self.settings_widget, "⚙️ 设置")
        
        # 记忆页
        self.memory_widget = MemoryWidget()
        right_panel.addTab(self.memory_widget, "📝 记忆")
        
        layout.addWidget(right_panel, 2)
        
        # 设置窗口样式
        self.setStyleSheet("""
            QMainWindow {
                background: #f5f5f5;
            }
            QWidget {
                font-family: "Microsoft YaHei", "Segoe UI", sans-serif;
            }
        """)


def main():
    app = QApplication(sys.argv)
    app.setStyle("Fusion")
    
    # 设置应用字体
    font = QFont("Microsoft YaHei", 10)
    app.setFont(font)
    
    window = MainWindow()
    window.show()
    
    sys.exit(app.exec_())


if __name__ == "__main__":
    main()
