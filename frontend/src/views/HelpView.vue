<template>
  <div class="help-view-unified">
    <PageHeader
      title="❓ 帮助中心"
      subtitle="常见问题与使用指南"
      :gradient-from="theme.gradientFrom"
      :gradient-to="theme.gradientTo"
    />

    <div class="page-content-wrapper">
      <!-- 搜索框 -->
      <div class="search-section">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索问题..."
          class="search-input"
        />
      </div>

      <!-- 帮助分类 -->
      <ContentCard>
        <template #header>
          <TabPane v-model="activeCategory" :tabs="categories" />
        </template>

        <template #default>
          <div v-if="activeCategory === 'rules'" class="category-content">
            <div class="help-section">
              <h4>游戏规则</h4>
              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(0)">
                  <span>什么是五子棋？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 0 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 0" class="faq-answer">
                  <p>五子棋是一种两人对弈的棋类游戏，双方分别使用黑白两色棋子，下在棋盘的交叉点上。先形成五子连线者获胜。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(1)">
                  <span>如何赢得比赛？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 1 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 1" class="faq-answer">
                  <p>当你在横、竖、斜任意方向上连成五个或更多同色棋子时，即可获胜。同时要注意防守对手的进攻。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(2)">
                  <span>什么是禁手？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 2 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 2" class="faq-answer">
                  <p>在某些规则模式下，黑方先行有优势，因此设置了禁手规则。黑方不能下出"三三"、"四四"和"长连"（六子或以上）。</p>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="activeCategory === 'match'" class="category-content">
            <div class="help-section">
              <h4>匹配对战</h4>
              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(3)">
                  <span>如何开始匹配？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 3 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 3" class="faq-answer">
                  <p>进入"匹配"页面，选择游戏模式（经典/竞技），点击"开始匹配"按钮即可。系统会根据你的积分匹配相近的对手。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(4)">
                  <span>匹配需要多长时间？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 4 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 4" class="faq-answer">
                  <p>匹配时间通常在30秒到2分钟之间，具体时间取决于当前在线人数和你的积分范围。积分范围设置越宽，匹配越快。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(5)">
                  <span>如何取消匹配？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 5 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 5" class="faq-answer">
                  <p>在匹配过程中，点击"取消匹配"按钮即可退出匹配队列。</p>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="activeCategory === 'ranking'" class="category-content">
            <div class="help-section">
              <h4>积分系统</h4>
              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(6)">
                  <span>积分是如何计算的？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 6 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 6" class="faq-answer">
                  <p>使用ELO积分系统。胜利获得积分，失败扣除积分。对手积分越高，获胜后获得的积分越多；对手积分越低，获胜获得的积分越少。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(7)">
                  <span>如何提升等级？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 7 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 7" class="faq-answer">
                  <p>通过完成对局获得经验值（EXP）。经验值累积到一定程度后等级会提升。每局游戏都会根据对局时长和结果给予经验值。</p>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="activeCategory === 'features'" class="category-content">
            <div class="help-section">
              <h4>功能说明</h4>
              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(8)">
                  <span>如何添加好友？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 8 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 8" class="faq-answer">
                  <p>进入"好友"页面，在"查找玩家"标签中输入用户名搜索，点击"添加好友"按钮发送好友请求。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(9)">
                  <span>如何创建房间？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 9 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 9" class="faq-answer">
                  <p>在"房间"页面点击"创建房间"，设置房间名称和密码（可选），然后将房间链接分享给好友即可。</p>
                </div>
              </div>

              <div class="faq-item">
                <div class="faq-question" @click="toggleFaq(10)">
                  <span>什么是残局挑战？</span>
                  <span :class="['arrow', { expanded: expandedFaq === 10 }]">▼</span>
                </div>
                <div v-show="expandedFaq === 10" class="faq-answer">
                  <p>残局挑战是预设好的棋局，你需要按照要求在限定步数内完成目标。这是提升棋力的好方法，有多个难度级别可供选择。</p>
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="activeCategory === 'contact'" class="category-content">
            <div class="help-section">
              <h4>联系我们</h4>
              <div class="contact-list">
                <div class="contact-item">
                  <div class="contact-icon">📧</div>
                  <div class="contact-info">
                    <div class="contact-title">邮箱支持</div>
                    <div class="contact-value">support@gobang.game</div>
                  </div>
                </div>

                <div class="contact-item">
                  <div class="contact-icon">💬</div>
                  <div class="contact-info">
                    <div class="contact-title">在线客服</div>
                    <div class="contact-value">工作日 9:00-18:00</div>
                  </div>
                </div>

                <div class="contact-item">
                  <div class="contact-icon">📱</div>
                  <div class="contact-info">
                    <div class="contact-title">反馈建议</div>
                    <div class="contact-value">我们在每页都提供了反馈入口</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </template>
      </ContentCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPageTheme, applyPageTheme } from '@/composables/usePageTheme'
import { PageHeader, ContentCard, TabPane } from '@/components/shared'

// 获取页面主题
const theme = getPageTheme('help')

// 应用主题
onMounted(() => {
  applyPageTheme(theme)
})

const activeCategory = ref('rules')
const searchQuery = ref('')
const expandedFaq = ref<number | null>(null)

const categories = [
  { label: '游戏规则', value: 'rules' },
  { label: '匹配对战', value: 'match' },
  { label: '积分系统', value: 'ranking' },
  { label: '功能说明', value: 'features' },
  { label: '联系我们', value: 'contact' }
]

const toggleFaq = (index: number) => {
  if (expandedFaq.value === index) {
    expandedFaq.value = null
  } else {
    expandedFaq.value = index
  }
}
</script>

<style scoped>
.help-view-unified {
  min-height: 100vh;
  padding-bottom: 80px;
}

.page-content-wrapper {
  max-width: 800px;
  margin: 0 auto;
  padding: 0 20px;
}

.search-section {
  margin: 20px 0;
}

.search-input {
  width: 100%;
  padding: 14px 20px;
  border: 1px solid #e0e0e0;
  border-radius: 12px;
  font-size: 15px;
  outline: none;
  transition: all 0.3s;
  box-sizing: border-box;
}

.search-input:focus {
  border-color: #fcb69f;
  box-shadow: 0 0 0 3px rgba(252, 182, 159, 0.1);
}

.category-content {
  padding: 20px 0;
}

.help-section h4 {
  font-size: 18px;
  color: #fcb69f;
  margin-bottom: 20px;
  font-weight: 600;
}

.faq-item {
  border: 1px solid #f0f0f0;
  border-radius: 10px;
  margin-bottom: 12px;
  overflow: hidden;
}

.faq-question {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 18px;
  cursor: pointer;
  transition: background 0.3s;
  font-weight: 500;
  color: #333;
}

.faq-question:hover {
  background: #fff8f5;
}

.arrow {
  font-size: 12px;
  color: #999;
  transition: transform 0.3s;
}

.arrow.expanded {
  transform: rotate(180deg);
}

.faq-answer {
  padding: 0 18px 18px;
  color: #666;
  line-height: 1.6;
  border-top: 1px solid #f0f0f0;
  padding-top: 15px;
}

.faq-answer p {
  margin: 0;
}

.contact-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 18px;
  background: #fafafa;
  border-radius: 10px;
}

.contact-icon {
  font-size: 32px;
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 50%;
}

.contact-info {
  flex: 1;
}

.contact-title {
  font-size: 15px;
  font-weight: 500;
  color: #333;
  margin-bottom: 3px;
}

.contact-value {
  font-size: 13px;
  color: #999;
}

@media (max-width: 768px) {
  .search-input {
    font-size: 14px;
    padding: 12px 16px;
  }

  .faq-question {
    padding: 12px 15px;
    font-size: 14px;
  }

  .faq-answer {
    padding: 0 15px 15px;
    font-size: 14px;
  }
}
</style>
