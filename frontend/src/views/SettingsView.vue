<template>
  <div class="settings-view-unified">
    <PageHeader
      title="⚙️ 设置"
      subtitle="个性化你的游戏体验"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <ContentCard>
        <template #default>
          <div class="settings-content">
            <!-- 音频设置 -->
            <div class="setting-section">
              <h4>🔊 音频设置</h4>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">启用音效</div>
                  <div class="setting-desc">游戏中的落子、胜利等音效</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.soundEnabled }]"
                    @click="settings.soundEnabled = !settings.soundEnabled"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">音效音量</div>
                </div>
                <div class="setting-control">
                  <input
                    v-model.number="settings.soundVolume"
                    type="range"
                    min="0"
                    max="100"
                    :disabled="!settings.soundEnabled"
                    class="slider-input"
                  />
                  <span class="slider-value">{{ settings.soundVolume }}</span>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">启用背景音乐</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.musicEnabled }]"
                    @click="settings.musicEnabled = !settings.musicEnabled"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">音乐音量</div>
                </div>
                <div class="setting-control">
                  <input
                    v-model.number="settings.musicVolume"
                    type="range"
                    min="0"
                    max="100"
                    :disabled="!settings.musicEnabled"
                    class="slider-input"
                  />
                  <span class="slider-value">{{ settings.musicVolume }}</span>
                </div>
              </div>
            </div>

            <!-- 游戏设置 -->
            <div class="setting-section">
              <h4>🎮 游戏设置</h4>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">棋盘主题</div>
                </div>
                <div class="setting-control">
                  <div class="radio-group">
                    <button
                      v-for="theme in boardThemes"
                      :key="theme.value"
                      :class="['radio-btn', { active: settings.boardTheme === theme.value }]"
                      @click="settings.boardTheme = theme.value"
                    >
                      {{ theme.label }}
                    </button>
                  </div>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">显示落子提示</div>
                  <div class="setting-desc">显示可以落子的位置提示</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.showMoveHint }]"
                    @click="settings.showMoveHint = !settings.showMoveHint"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">显示最后落子</div>
                  <div class="setting-desc">高亮显示最后一步棋</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.showLastMove }]"
                    @click="settings.showLastMove = !settings.showLastMove"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 界面设置 -->
            <div class="setting-section">
              <h4>🎨 界面设置</h4>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">显示积分</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.showRating }]"
                    @click="settings.showRating = !settings.showRating"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">显示等级</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.showLevel }]"
                    @click="settings.showLevel = !settings.showLevel"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">语言</div>
                </div>
                <div class="setting-control">
                  <select v-model="settings.language" class="select-input">
                    <option value="zh-CN">简体中文</option>
                    <option value="zh-TW">繁體中文</option>
                    <option value="en">English</option>
                    <option value="ja">日本語</option>
                  </select>
                </div>
              </div>
            </div>

            <!-- 匹配设置 -->
            <div class="setting-section">
              <h4>⚔️ 匹配设置</h4>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">自动匹配</div>
                  <div class="setting-desc">进入匹配页面后自动开始匹配</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.autoMatch }]"
                    @click="settings.autoMatch = !settings.autoMatch"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">匹配范围</div>
                  <div class="setting-desc">积分差距范围</div>
                </div>
                <div class="setting-control">
                  <input
                    v-model.number="settings.matchRange"
                    type="range"
                    min="0"
                    max="500"
                    step="50"
                    class="slider-input"
                  />
                  <span class="slider-value">±{{ settings.matchRange }}</span>
                </div>
              </div>
            </div>

            <!-- 隐私设置 -->
            <div class="setting-section">
              <h4>🔒 隐私设置</h4>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">允许观战</div>
                  <div class="setting-desc">允许其他玩家观看你的对局</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.allowSpectate }]"
                    @click="settings.allowSpectate = !settings.allowSpectate"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">显示在线状态</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.showOnlineStatus }]"
                    @click="settings.showOnlineStatus = !settings.showOnlineStatus"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">接受好友请求</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: settings.acceptFriendRequests }]"
                    @click="settings.acceptFriendRequests = !settings.acceptFriendRequests"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 通知设置 -->
            <div class="setting-section">
              <h4>🔔 通知设置</h4>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">匹配成功通知</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: notifications.matchSuccess }]"
                    @click="notifications.matchSuccess = !notifications.matchSuccess"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">游戏邀请通知</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: notifications.gameInvite }]"
                    @click="notifications.gameInvite = !notifications.gameInvite"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>

              <div class="setting-item">
                <div class="setting-info">
                  <div class="setting-label">好友消息通知</div>
                </div>
                <div class="setting-control">
                  <button
                    :class="['toggle-btn', { active: notifications.friendMessage }]"
                    @click="notifications.friendMessage = !notifications.friendMessage"
                  >
                    <span class="toggle-slider"></span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="save-actions">
              <ActionButton variant="ghost" @click="resetSettings">
                重置默认
              </ActionButton>
              <ActionButton
                variant="primary"
                :loading="saving"
                @click="saveSettings"
              >
                保存设置
              </ActionButton>
            </div>
          </div>
        </template>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, ActionButton } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('settings')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
  fetchSettings()
})

const saving = ref(false)

const settings = reactive({
  // 音频设置
  soundEnabled: true,
  soundVolume: 80,
  musicEnabled: true,
  musicVolume: 60,

  // 游戏设置
  boardTheme: 'classic',
  showMoveHint: true,
  showLastMove: true,

  // 界面设置
  showRating: true,
  showLevel: true,
  language: 'zh-CN',

  // 匹配设置
  autoMatch: true,
  matchRange: 200,

  // 隐私设置
  allowSpectate: true,
  showOnlineStatus: true,
  acceptFriendRequests: true
})

const notifications = reactive({
  matchSuccess: true,
  gameInvite: true,
  friendMessage: true,
  systemMessage: true
})

const boardThemes = [
  { label: '经典木纹', value: 'classic' },
  { label: '现代简约', value: 'modern' },
  { label: '暗黑模式', value: 'dark' }
]

const defaultSettings = JSON.parse(JSON.stringify(settings))
const defaultNotifications = JSON.parse(JSON.stringify(notifications))

const fetchSettings = async () => {
  try {
    const saved = localStorage.getItem('userSettings')
    if (saved) {
      const savedSettings = JSON.parse(saved)
      Object.assign(settings, savedSettings.settings || {})
      Object.assign(notifications, savedSettings.notifications || {})
    }
  } catch (error) {
    console.error('Failed to fetch settings:', error)
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    localStorage.setItem('userSettings', JSON.stringify({
      settings: JSON.parse(JSON.stringify(settings)),
      notifications: JSON.parse(JSON.stringify(notifications))
    }))

  } catch (error) {
    ElMessage.error('保存设置失败')
  } finally {
    saving.value = false
  }
}

const resetSettings = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要重置所有设置为默认值吗？',
      '确认重置',
      { type: 'warning' }
    )

    Object.assign(settings, JSON.parse(JSON.stringify(defaultSettings)))
    Object.assign(notifications, JSON.parse(JSON.stringify(defaultNotifications)))
  } catch {
    // 用户取消
  }
}
</script>

<style scoped>
.settings-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 700px;
  margin: 0 auto;
  padding: 0 20px;
}

.settings-content {
  padding: 20px 0;
}

.setting-section {
  padding: 25px 0;
  border-bottom: 1px solid #f0f0f0;
}

.setting-section:last-child {
  border-bottom: none;
}

.setting-section h4 {
  font-size: 18px;
  color: #868f96;
  margin-bottom: 20px;
  font-weight: 600;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 0;
  gap: 20px;
}

.setting-info {
  flex: 1;
}

.setting-label {
  font-size: 15px;
  color: #333;
  margin-bottom: 3px;
}

.setting-desc {
  font-size: 13px;
  color: #999;
}

.setting-control {
  display: flex;
  align-items: center;
  gap: 15px;
  flex-shrink: 0;
}

/* 开关按钮 */
.toggle-btn {
  width: 50px;
  height: 28px;
  background: #e0e0e0;
  border-radius: 14px;
  position: relative;
  cursor: pointer;
  transition: background 0.3s;
  border: none;
  padding: 0;
}

.toggle-btn.active {
  background: linear-gradient(135deg, #868f96 0%, #596164 100%);
}

.toggle-slider {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 22px;
  height: 22px;
  background: white;
  border-radius: 50%;
  transition: transform 0.3s;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.toggle-btn.active .toggle-slider {
  transform: translateX(22px);
}

/* 滑块输入 */
.slider-input {
  width: 150px;
  height: 6px;
  -webkit-appearance: none;
  background: linear-gradient(to right, #868f96 0%, #e0e0e0 100%);
  border-radius: 3px;
  outline: none;
}

.slider-input::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 18px;
  height: 18px;
  background: #868f96;
  border-radius: 50%;
  cursor: pointer;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

.slider-input:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.slider-value {
  min-width: 40px;
  text-align: right;
  font-size: 14px;
  color: #868f96;
  font-weight: 500;
}

/* 单选按钮组 */
.radio-group {
  display: flex;
  gap: 8px;
}

.radio-btn {
  padding: 8px 16px;
  border: 1px solid #e0e0e0;
  background: white;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 14px;
  color: #666;
}

.radio-btn:hover {
  border-color: #868f96;
  color: #868f96;
}

.radio-btn.active {
  background: linear-gradient(135deg, #868f96 0%, #596164 100%);
  color: white;
  border-color: transparent;
}

/* 下拉选择 */
.select-input {
  padding: 8px 30px 8px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  color: #333;
  outline: none;
  cursor: pointer;
  background: white url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='12' height='12' viewBox='0 0 12 12'%3E%3Cpath fill='%23999' d='M6 8L2 4h8z'/%3E%3C/svg%3E") no-repeat right 10px center;
  appearance: none;
}

.select-input:focus {
  border-color: #868f96;
}

/* 操作按钮 */
.save-actions {
  display: flex;
  justify-content: center;
  gap: 15px;
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid #f0f0f0;
}

@media (max-width: 768px) {
  .setting-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .setting-control {
    width: 100%;
    justify-content: flex-end;
  }

  .radio-group {
    flex-wrap: wrap;
  }

  .slider-input {
    flex: 1;
  }

  .save-actions {
    flex-direction: column;
  }
}
</style>
