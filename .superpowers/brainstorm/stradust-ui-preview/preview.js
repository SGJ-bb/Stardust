/* ============================================
   Stradust (星尘) UI Preview - 交互逻辑
   ============================================ */

(function() {
  'use strict';

  // 主题配置
  const THEMES = [
    { id: 'sakura', name: '樱粉', color: '#ec4899' },
    { id: 'peach', name: '桃粉', color: '#f97316' },
    { id: 'violet', name: '紫罗兰', color: '#8b5cf6' },
    { id: 'ocean', name: '海蓝', color: '#3b82f6' },
    { id: 'emerald', name: '翡翠', color: '#10b981' },
    { id: 'sunset', name: '日落', color: '#fb923c' },
    { id: 'rose-gold', name: '玫瑰金', color: '#e8a0a8' },
    { id: 'mint', name: '薄荷', color: '#14b8a6' },
    { id: 'tea', name: '茶香', color: '#c4a882' },
    { id: 'china', name: '华夏', color: '#e0454a' },
    { id: 'dark-night', name: '暗夜', color: '#60a5fa' },
    { id: 'cyberpunk', name: '赛博朋克', color: '#00f0ff' }
  ];

  // 页面配置
  const PAGES = [
    { id: 'chat', name: '💬 聊天', icon: '💬' },
    { id: 'diary', name: '📔 日记', icon: '📔' },
    { id: 'checkin', name: '✅ 签到', icon: '✅' },
    { id: 'album', name: '🖼️ 相册', icon: '🖼️' },
    { id: 'achievement', name: '🏆 成就', icon: '🏆' },
    { id: 'virtual-world', name: '🌍 虚拟世界', icon: '🌍' },
    { id: 'settings', name: '⚙️ 设置', icon: '⚙️' },
    { id: 'profile', name: '👤 个人中心', icon: '👤' }
  ];

  // 状态管理
  let currentTheme = 'sakura';
  let isDarkMode = false;
  let currentPage = 'chat';

  // DOM 元素缓存
  const elements = {};

  /**
   * 初始化应用
   */
  function init() {
    cacheElements();
    setupThemeSelector();
    setupDarkModeToggle();
    setupPageNavigation();
    setupQuickNav();
    setupSettingsInteractions();
    applyTheme();
    showPage('chat');
  }

  /**
   * 缓存DOM元素
   */
  function cacheElements() {
    elements.phoneScreen = document.getElementById('phone-screen');
    elements.themeSelect = document.getElementById('theme-select');
    elements.darkModeBtn = document.getElementById('dark-mode-btn');
    elements.pageSelect = document.getElementById('page-select');
    elements.quickNav = document.getElementById('quick-nav');
    elements.settingsThemeSelector = document.getElementById('settings-theme-selector');
  }

  /**
   * 设置主题选择器
   */
  function setupThemeSelector() {
    if (!elements.themeSelect) return;

    THEMES.forEach(theme => {
      const option = document.createElement('option');
      option.value = theme.id;
      option.textContent = theme.name;
      elements.themeSelect.appendChild(option);
    });

    elements.themeSelect.value = currentTheme;
    elements.themeSelect.addEventListener('change', (e) => {
      currentTheme = e.target.value;
      applyTheme();
      updateSettingsThemeSwatches();
    });
  }

  /**
   * 设置暗色模式切换
   */
  function setupDarkModeToggle() {
    if (!elements.darkModeBtn) return;

    elements.darkModeBtn.addEventListener('click', () => {
      isDarkMode = !isDarkMode;
      applyTheme();
      updateDarkModeButton();
      updateSettingsDarkSwitch();
    });

    updateDarkModeButton();
  }

  /**
   * 更新暗色模式按钮图标
   */
  function updateDarkModeButton() {
    if (elements.darkModeBtn) {
      elements.darkModeBtn.textContent = isDarkMode ? '☀️' : '🌙';
      elements.darkModeBtn.title = isDarkMode ? '切换到浅色模式' : '切换到暗色模式';
    }
  }

  /**
   * 应用主题
   */
  function applyTheme() {
    const phoneScreen = document.querySelector('.phone-frame') || document.body;

    // 移除旧的主题类
    document.body.classList.remove('light', 'dark');

    // 处理特殊主题（暗夜、赛博朋克只有暗色）
    const specialThemes = ['dark-night', 'cyberpunk'];
    if (specialThemes.includes(currentTheme)) {
      isDarkMode = true;
      document.body.setAttribute('data-theme', currentTheme);
      document.body.classList.remove('light');
      document.body.classList.add('dark');
    } else {
      document.body.setAttribute('data-theme', currentTheme);
      document.body.classList.add(isDarkMode ? 'dark' : 'light');
    }

    // 同步下拉框
    if (elements.themeSelect && elements.themeSelect.value !== currentTheme) {
      elements.themeSelect.value = currentTheme;
    }

    // 更新控制栏样式
    updateControlBarStyles();

    console.log(`[Stradust Preview] Theme applied: ${currentTheme} (${isDarkMode ? 'dark' : 'light'})`);
  }

  /**
   * 更新控制栏的CSS变量继承
   */
  function updateControlBarStyles() {
    const controlBar = document.querySelector('.control-bar');
    const headerBar = document.querySelector('.header-bar');
    const quickNav = document.querySelector('.quick-nav');

    [controlBar, headerBar, quickNav].forEach(el => {
      if (el) {
        el.style.setProperty('--primary', getComputedStyle(document.body).getPropertyValue('--primary').trim());
        el.style.setProperty('--surface', getComputedStyle(document.body).getPropertyValue('--surface').trim());
        el.style.setProperty('--text-primary', getComputedStyle(document.body).getPropertyValue('--text-primary').trim());
        el.style.setProperty('--tertiary', getComputedStyle(document.body).getPropertyValue('--tertiary').trim());
      }
    });
  }

  /**
   * 设置页面导航
   */
  function setupPageNavigation() {
    // 下拉菜单切换
    if (elements.pageSelect) {
      PAGES.forEach(page => {
        const option = document.createElement('option');
        option.value = page.id;
        option.textContent = page.name;
        elements.pageSelect.appendChild(option);
      });

      elements.pageSelect.addEventListener('change', (e) => {
        showPage(e.target.value);
      });
    }

    // 底部导航点击
    document.querySelectorAll('.nav-item').forEach(item => {
      item.addEventListener('click', () => {
        const pageId = item.dataset.page;
        showPage(pageId);
      });
    });
  }

  /**
   * 显示指定页面
   */
  function showPage(pageId) {
    currentPage = pageId;

    // 隐藏所有页面
    document.querySelectorAll('.page').forEach(page => {
      page.classList.remove('active');
    });

    // 显示目标页面
    const targetPage = document.getElementById(`page-${pageId}`);
    if (targetPage) {
      targetPage.classList.add('active');
    }

    // 更新底部导航高亮
    document.querySelectorAll('.nav-item').forEach(item => {
      item.classList.toggle('active', item.dataset.page === pageId);
    });

    // 更新快速导航高亮
    document.querySelectorAll('.quick-nav-btn').forEach(btn => {
      btn.classList.toggle('active', btn.dataset.page === pageId);
    });

    // 同步下拉框
    if (elements.pageSelect && elements.pageSelect.value !== pageId) {
      elements.pageSelect.value = pageId;
    }

    // 滚动到顶部
    if (elements.phoneScreen) {
      elements.phoneScreen.scrollTop = 0;
    }

    console.log(`[Stradust Preview] Page switched to: ${pageId}`);
  }

  /**
   * 设置快速导航
   */
  function setupQuickNav() {
    if (!elements.quickNav) return;

    PAGES.forEach(page => {
      const btn = document.createElement('button');
      btn.className = 'quick-nav-btn';
      btn.dataset.page = page.id;
      btn.textContent = page.name.split(' ')[1] || page.name;
      btn.addEventListener('click', () => showPage(page.id));
      elements.quickNav.appendChild(btn);
    });
  }

  /**
   * 设置设置页面的交互
   */
  function setupSettingsInteractions() {
    // 设置页面内的主题色块
    renderSettingsThemeSwatches();

    // 深色模式开关
    const darkSwitch = document.getElementById('settings-dark-switch');
    if (darkSwitch) {
      darkSwitch.addEventListener('click', () => {
        isDarkMode = !isDarkMode;
        applyTheme();
        updateDarkModeButton();
        updateSettingsDarkSwitch();
      });
    }

    // 滑块值显示
    setupSliderValueDisplay('temperature-slider', 'temperature-value');
    setupSliderValueDisplay('token-slider', 'token-value');
    setupSliderValueDisplay('speed-slider', 'speed-value');
  }

  /**
   * 渲染设置页面的主题色块
   */
  function renderSettingsThemeSwatches() {
    const container = document.getElementById('settings-theme-selector');
    if (!container) return;

    container.innerHTML = '';

    THEMES.forEach(theme => {
      const swatch = document.createElement('div');
      swatch.className = `theme-swatch ${theme.id === currentTheme ? 'active' : ''}`;
      swatch.style.background = theme.color;
      swatch.title = theme.name;
      swatch.dataset.theme = theme.id;
      swatch.addEventListener('click', () => {
        currentTheme = theme.id;
        applyTheme();
        updateSettingsThemeSwatches();
        if (elements.themeSelect) elements.themeSelect.value = currentTheme;
      });
      container.appendChild(swatch);
    });
  }

  /**
   * 更新设置页面的主题色块状态
   */
  function updateSettingsThemeSwatches() {
    document.querySelectorAll('#settings-theme-selector .theme-swatch').forEach(swatch => {
      swatch.classList.toggle('active', swatch.dataset.theme === currentTheme);
    });
  }

  /**
   * 更新设置页面的暗色模式开关状态
   */
  function updateSettingsDarkSwitch() {
    const switchEl = document.getElementById('settings-dark-switch');
    if (switchEl) {
      switchEl.classList.toggle('on', isDarkMode);
    }
  }

  /**
   * 设置滑块的值显示
   */
  function setupSliderValueDisplay(sliderId, valueId) {
    const slider = document.getElementById(sliderId);
    const valueDisplay = document.getElementById(valueId);

    if (slider && valueDisplay) {
      const updateValue = () => {
        valueDisplay.textContent = slider.value;
      };
      slider.addEventListener('input', updateValue);
      updateValue(); // 初始值
    }
  }

  /**
   * 日记页面日期选择
   */
  window.setupDateSelector = function() {
    document.querySelectorAll('.date-item').forEach(item => {
      item.addEventListener('click', () => {
        document.querySelectorAll('.date-item').forEach(i => i.classList.remove('active'));
        item.classList.add('active');
      });
    });
  };

  /**
   * 签到按钮动画增强
   */
  window.setupCheckinButton = function() {
    const circle = document.querySelector('.checkin-circle');
    if (!circle) return;

    circle.addEventListener('click', function() {
      this.style.transform = 'scale(0.95)';
      setTimeout(() => {
        this.style.transform = 'scale(1.05)';
        setTimeout(() => {
          this.style.transform = '';
        }, 200);
      }, 100);
    });
  };

  /**
   * 成就页面筛选芯片
   */
  window.setupFilterChips = function() {
    document.querySelectorAll('.filter-chips .chip').forEach(chip => {
      chip.addEventListener('click', () => {
        document.querySelectorAll('.filter-chips .chip').forEach(c => c.classList.remove('active'));
        chip.classList.add('active');
      });
    });
  };

  /**
   * 虚拟世界场景Tab
   */
  window.setupSceneTabs = function() {
    document.querySelectorAll('.scene-tab').forEach(tab => {
      tab.addEventListener('click', () => {
        document.querySelectorAll('.scene-tab').forEach(t => t.classList.remove('active'));
        tab.classList.add('active');
      });
    });
  };

  // 初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // 暴露全局方法供HTML调用
  window.StradustPreview = {
    showPage,
    applyTheme,
    getCurrentState: () => ({ currentTheme, isDarkMode, currentPage })
  };

})();
