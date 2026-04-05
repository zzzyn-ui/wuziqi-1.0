<template>
  <div class="home-view">
    <!-- ========== 顶部 Header ========== -->
    <div class="header">
      <!-- 粒子背景 -->
      <canvas id="headerParticles" class="header-particles"></canvas>

      <div class="header-left">
        <!-- Logo -->
        <div class="logo-large">
          <svg viewBox="0 0 100 100" fill="none" xmlns="http://www.w3.org/2000/svg">
            <circle cx="50" cy="50" r="45" fill="rgba(255,255,255,0.3)" stroke="white" stroke-width="2"/>
            <circle cx="35" cy="35" r="8" fill="white"/>
            <circle cx="65" cy="65" r="8" fill="white"/>
            <line x1="35" y1="35" x2="65" y2="65" stroke="white" stroke-width="2"/>
            <line x1="65" y1="35" x2="35" y2="65" stroke="white" stroke-width="2"/>
          </svg>
        </div>

        <!-- 标题 -->
        <div class="header-title">
          <h1>五子棋对战平台</h1>
          <p>实时在线对局 · 智能匹配系统</p>
        </div>
      </div>

      <div class="header-right">
        <!-- 用户信息显示 -->
        <div class="user-profile-display" @click="toggleUserInfoCard">
          <div class="header-avatar-small">
            {{ userStore.userInfo?.nickname?.charAt(0) || '?' }}
          </div>
          <div class="header-user-name">{{ userStore.userInfo?.nickname || '游客' }}</div>
          <div class="header-user-level">Lv.{{ userStore.userInfo?.level || 1 }}</div>
        </div>

      </div>

      <!-- 用户信息卡片（弹窗） - 简化版 -->
      <div class="user-info-card user-info-card-simple" :class="{ active: showUserInfoCard }" @click.stop>
        <div class="user-info-card-content">
          <div class="user-info-header-simple">
            <div class="user-info-names-simple">
              <div class="user-info-nickname-simple">{{ userStore.userInfo?.nickname || '游客' }}</div>
            </div>
            <button class="user-info-close" @click="showUserInfoCard = false">✕</button>
          </div>

          <div class="user-info-actions">
            <button class="user-info-btn primary" @click="goToProfile">
              <span>👤</span> 个人中心
            </button>
            <button class="user-info-btn secondary" @click="handleLogout">
              <span>🚪</span> 退出登录
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 横向滚动导航（移动端） ========== -->
    <div class="horizontal-nav">
      <!-- 左侧拖拽手柄 - 向左箭头 -->
      <div class="drag-handle drag-handle-left" @click.stop="scrollHorizontalNav(-200)">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z"/>
        </svg>
      </div>

      <div class="horizontal-nav-scroll" ref="horizontalNavScroll">
        <div
          v-for="item in navItems"
          :key="item.id"
          class="horizontal-nav-item"
          :class="{ active: activeNav === item.id }"
          @click="handleNavClick(item)"
        >
          <span class="horizontal-nav-icon">{{ item.icon }}</span>
          <span>{{ item.name }}</span>
        </div>
      </div>

      <!-- 右侧拖拽手柄 - 向右箭头 -->
      <div class="drag-handle drag-handle-right" @click.stop="scrollHorizontalNav(200)">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z"/>
        </svg>
      </div>
    </div>

    <!-- ========== 主容器 ========== -->
    <div class="main-container">
      <!-- 左侧边栏 -->
      <div class="sidebar">
        <div class="nav-section">
          <div class="sidebar-title">📱 开始游戏</div>
          <div
            v-for="item in gameNavItems"
            :key="item.id"
            class="nav-item"
            :class="{ active: activeNav === item.id }"
            @click="handleNavClick(item)"
          >
            <div class="nav-icon">{{ item.icon }}</div>
            <span>{{ item.name }}</span>
          </div>
        </div>

        <div class="nav-divider"></div>

        <div class="nav-section">
          <div class="sidebar-title">🏆 社区互动</div>
          <div
            v-for="item in communityNavItems"
            :key="item.id"
            class="nav-item"
            @click="handleNavClick(item)"
          >
            <div class="nav-icon">{{ item.icon }}</div>
            <span>{{ item.name }}</span>
          </div>
        </div>
      </div>

      <!-- 内容区 -->
      <div class="content-area">
        <!-- 人机对战面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'pve' }">
          <!-- Hero Banner -->
          <div class="hero-banner">
            <div class="hero-content">
              <div class="hero-text">
                <h2>🤖 人机对战</h2>
                <p>挑战不同难度的 AI 对手，提升你的棋艺！</p>
              </div>
              <div class="hero-animation">
                <div class="board-preview">
                  <div class="preview-line"></div>
                  <div class="preview-line"></div>
                  <div class="preview-line"></div>
                  <div class="preview-line"></div>
                  <div class="preview-line"></div>
                  <div class="piece-anim" style="--delay: 0s;"></div>
                  <div class="piece-anim" style="--delay: 1s;"></div>
                  <div class="piece-anim" style="--delay: 2s;"></div>
                  <div class="piece-anim" style="--delay: 3s;"></div>
                </div>
              </div>
            </div>
          </div>

          <!-- 难度选择 -->
          <div class="pve-cards">
            <div class="pve-card" @click="startPVE('easy')">
              <div class="diff-icon-large">🌱</div>
              <div class="diff-title">简单模式</div>
              <div class="diff-desc">适合初学者，AI 会犯错</div>
              <div class="diff-stars-large">
                <span class="star filled">★</span>
                <span class="star">★</span>
                <span class="star">★</span>
              </div>
            </div>

            <div class="pve-card" @click="startPVE('medium')">
              <div class="diff-icon-large">🌿</div>
              <div class="diff-title">中等模式</div>
              <div class="diff-desc">有一定挑战性</div>
              <div class="diff-stars-large">
                <span class="star filled">★</span>
                <span class="star filled">★</span>
                <span class="star">★</span>
              </div>
            </div>

            <div class="pve-card" @click="startPVE('hard')">
              <div class="diff-icon-large">🌳</div>
              <div class="diff-title">困难模式</div>
              <div class="diff-desc">真正的挑战！</div>
              <div class="diff-stars-large">
                <span class="star filled">★</span>
                <span class="star filled">★</span>
                <span class="star filled">★</span>
              </div>
            </div>
          </div>

          <!-- 游戏规则 -->
          <div class="pve-rules-section">
            <h3>📖 游戏规则</h3>
            <ul>
              <li>黑方先行，双方轮流落子</li>
              <li>先形成五子连珠者获胜</li>
              <li>可以横、竖、斜方向连线</li>
              <li>简单模式下 AI 会偶尔失误</li>
            </ul>
          </div>
        </div>

        <!-- 快速匹配面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'quick-match' }">
          <div class="hero-banner hero-banner-online">
            <div class="hero-content">
              <div class="hero-text">
                <h2>⚡ 快速匹配</h2>
                <p>系统自动匹配与你水平相近的对手</p>
              </div>
              <div class="hero-animation">
                <div class="players-preview">
                  <div class="player-preview player-black">
                    <div class="player-avatar-preview">⚫</div>
                    <div class="player-name-preview">黑方</div>
                  </div>
                  <div class="vs-badge">VS</div>
                  <div class="player-preview player-white">
                    <div class="player-avatar-preview">⚪</div>
                    <div class="player-name-preview">白方</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="match-modes">
            <div class="match-mode-card" @click="handleStartMatch('casual')">
              <div class="match-mode-icon">🎮</div>
              <div class="match-mode-title">休闲模式</div>
              <div class="match-mode-desc">轻松对局，不影响积分</div>
              <div class="match-mode-btn">点击开始</div>
            </div>

            <div class="match-mode-card primary" @click="handleStartMatch('ranked')">
              <div class="match-mode-icon">🏆</div>
              <div class="match-mode-title">竞技模式</div>
              <div class="match-mode-desc">ELO 积分对决，展实力</div>
              <div class="match-mode-btn">点击开始</div>
            </div>
          </div>

          <div class="match-info">
            <h3>💡 匹配说明</h3>
            <ul>
              <li>休闲模式：不计算积分，适合练习</li>
              <li>竞技模式：使用 ELO 算法计算积分变化</li>
              <li>系统会根据积分自动匹配相近水平的对手</li>
              <li>积分范围：1200（初始）~ 2200+（宗师）</li>
            </ul>
          </div>
        </div>

        <!-- 房间面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'room' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>🏠 房间对战</h2>
                <p>创建或加入房间，与好友对战</p>
              </div>
            </div>
          </div>

          <div class="room-actions">
            <button class="room-btn primary" @click="createRoom">
              <span class="room-btn-icon">➕</span>
              创建房间
            </button>
            <button class="room-btn" @click="joinRoom">
              <span class="room-btn-icon">🔍</span>
              加入房间
            </button>
          </div>

          <div class="room-list">
            <h3>📋 公开房间</h3>
            <div v-if="publicRooms.length === 0" class="empty-state">
              <p>暂无公开房间</p>
              <button class="refresh-btn" @click="loadPublicRooms">刷新</button>
            </div>
            <div v-else class="room-list-items">
              <div v-for="room in publicRooms" :key="room.id" class="room-item" @click="joinRoomById(room.id)">
                <div class="room-info">
                  <div class="room-name">{{ room.name }}</div>
                  <div class="room-meta">{{ room.mode }} · {{ room.playerCount }}/2</div>
                </div>
                <button class="join-mini-btn">加入</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 排行榜面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'rank' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #ffd700 0%, #ffed4e 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>🏆 排行榜</h2>
                <p>查看玩家积分排名，挑战顶尖高手</p>
              </div>
            </div>
          </div>

          <div class="rank-tabs">
            <div
              v-for="tab in rankTabs"
              :key="tab.value"
              class="rank-tab-item"
              :class="{ active: activeRankTab === tab.value }"
              @click="activeRankTab = tab.value; fetchRankList()"
            >
              {{ tab.label }}
            </div>
          </div>

          <div class="rank-content">
            <div v-if="rankLoading" class="loading-state">
              <div class="loading-spinner"></div>
              <p>加载中...</p>
            </div>

            <div v-else-if="rankList.length === 0" class="empty-state">
              <p>暂无数据</p>
            </div>

            <div v-else class="rank-items">
              <div
                v-for="(player, index) in rankList"
                :key="player.id"
                class="rank-item"
                :class="{ 'is-me': player.isMe }"
              >
                <div class="rank-number" :class="`rank-${getRankClass(index)}`">
                  <span v-if="index < 3" class="medal">
                    <span v-if="index === 0">🥇</span>
                    <span v-else-if="index === 1">🥈</span>
                    <span v-else>🥉</span>
                  </span>
                  <span v-else>{{ index + 1 }}</span>
                </div>

                <div class="player-avatar">
                  <div class="avatar-circle">{{ player.nickname?.charAt(0) || '?' }}</div>
                </div>

                <div class="player-info">
                  <div class="player-name">
                    {{ player.nickname || player.username }}
                    <span v-if="player.isMe" class="me-tag">我</span>
                    <span v-if="player.online" class="online-tag">在线</span>
                  </div>
                  <div class="player-level">Lv.{{ player.level }} {{ getLevelName(player.level) }}</div>
                </div>

                <div class="player-stats">
                  <div class="stat-item">
                    <span class="stat-label">积分</span>
                    <span class="stat-value">{{ player.rating }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">胜率</span>
                    <span class="stat-value">{{ player.winRate }}%</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-label">场次</span>
                    <span class="stat-value">{{ player.totalGames }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="my-rank-banner" v-if="myRank">
            <span class="my-rank-label">我的排名</span>
            <span class="my-rank-value">#{{ myRank }}</span>
          </div>
        </div>

        <!-- 好友系统面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'friends' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>👫 好友系统</h2>
                <p>添加好友，一起下棋</p>
              </div>
            </div>
          </div>

          <div class="friends-tabs">
            <div
              v-for="tab in friendsTabs"
              :key="tab.value"
              class="friends-tab-item"
              :class="{ active: activeFriendsTab === tab.value }"
              @click="activeFriendsTab = tab.value"
            >
              {{ tab.label }}
            </div>
          </div>

          <div class="friends-content">
            <div v-if="activeFriendsTab === 'list'" class="friends-list-section">
              <div class="section-header">
                <el-input
                  v-model="friendSearchText"
                  placeholder="搜索好友..."
                  style="width: 250px"
                  clearable
                />
                <el-button type="primary" @click="showAddFriendDialog = true">
                  ➕ 添加好友
                </el-button>
              </div>

              <div v-if="friendsList.length === 0" class="empty-state">
                <p>暂无好友，快去添加吧！</p>
              </div>

              <div v-else class="friend-items">
                <div v-for="friend in filteredFriends" :key="friend.id" class="friend-card">
                  <div class="friend-avatar-circle">{{ friend.nickname?.charAt(0) || '?' }}</div>
                  <div class="friend-details">
                    <div class="friend-name">{{ friend.nickname || friend.username }}</div>
                    <div class="friend-level">Lv.{{ friend.level }}</div>
                  </div>
                  <div class="friend-status" :class="{ online: friend.online }">
                    {{ friend.online ? '在线' : '离线' }}
                  </div>
                </div>
              </div>
            </div>

            <div v-if="activeFriendsTab === 'requests'" class="requests-section">
              <div class="section-header">
                <h3>好友申请</h3>
                <el-badge v-if="pendingRequests.length > 0" :value="pendingRequests.length" />
              </div>

              <div v-if="pendingRequests.length === 0" class="empty-state">
                <p>暂无好友申请</p>
              </div>

              <div v-else class="request-items">
                <div v-for="req in pendingRequests" :key="req.id" class="request-card">
                  <div class="request-avatar">{{ req.nickname?.charAt(0) || '?' }}</div>
                  <div class="request-info">
                    <div class="request-name">{{ req.nickname || req.username }}</div>
                    <div class="request-message">{{ req.message || '请求添加你为好友' }}</div>
                  </div>
                  <div class="request-actions">
                    <el-button type="success" size="small" @click="acceptRequest(req.id)">同意</el-button>
                    <el-button size="small" @click="rejectRequest(req.id)">拒绝</el-button>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="activeFriendsTab === 'search'" class="search-section">
              <div class="search-box">
                <el-input
                  v-model="playerSearchText"
                  placeholder="输入玩家用户名搜索"
                  @keyup.enter="searchPlayer"
                >
                  <template #append>
                    <el-button @click="searchPlayer">🔍</el-button>
                  </template>
                </el-input>
              </div>

              <div v-if="searchResults.length > 0" class="search-results">
                <div v-for="player in searchResults" :key="player.id" class="player-result-card">
                  <div class="player-avatar">{{ player.nickname?.charAt(0) || '?' }}</div>
                  <div class="player-info">
                    <div class="player-name">{{ player.nickname || player.username }}</div>
                    <div class="player-level">Lv.{{ player.level }}</div>
                  </div>
                  <el-button type="primary" size="small" @click="sendFriendRequest(player)">
                    添加
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 残局挑战面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'puzzle' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>📚 残局挑战</h2>
                <p>挑战经典残局，提升棋艺</p>
              </div>
            </div>
          </div>

          <div class="puzzle-difficulty-selector">
            <el-radio-group v-model="puzzleDifficulty" @change="fetchPuzzles">
              <el-radio-button value="beginner">初级</el-radio-button>
              <el-radio-button value="intermediate">中级</el-radio-button>
              <el-radio-button value="advanced">高级</el-radio-button>
              <el-radio-button value="expert">专家</el-radio-button>
            </el-radio-group>
          </div>

          <div class="puzzle-stats">
            <span>已完成: {{ puzzleCompletedCount }}</span>
            <span>准确率: {{ puzzleAccuracy }}%</span>
          </div>

          <div class="puzzle-list">
            <div v-if="puzzleLoading" class="loading-state">
              <div class="loading-spinner"></div>
              <p>加载中...</p>
            </div>

            <div v-else-if="puzzles.length === 0" class="empty-state">
              <p>暂无残局</p>
            </div>

            <div v-else class="puzzle-items">
              <div
                v-for="puzzle in puzzles"
                :key="puzzle.id"
                class="puzzle-card"
                :class="{ completed: puzzle.completed, failed: puzzle.failed }"
                @click="startPuzzle(puzzle)"
              >
                <div class="puzzle-number">{{ puzzle.id }}</div>
                <div class="puzzle-info">
                  <div class="puzzle-name">{{ puzzle.name }}</div>
                  <div class="puzzle-meta">
                    <span>难度: {{ puzzle.difficulty }}</span>
                    <span>步数: {{ puzzle.moves }}</span>
                  </div>
                </div>
                <div class="puzzle-status">
                  <span v-if="puzzle.completed">✅</span>
                  <span v-else-if="puzzle.failed">❌</span>
                  <span v-else>⏳</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 观战模式面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'spectate' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>👀 观战模式</h2>
                <p>观看高手对局，学习技巧</p>
              </div>
            </div>
          </div>

          <div class="spectate-content">
            <div class="spectate-tabs">
              <div
                v-for="tab in spectateTabs"
                :key="tab.value"
                class="spectate-tab-item"
                :class="{ active: activeSpectateTab === tab.value }"
                @click="activeSpectateTab = tab.value"
              >
                {{ tab.label }}
              </div>
            </div>

            <div class="spectate-games">
              <div v-if="activeSpectateTab === 'live'" class="live-games">
                <div v-if="liveGames.length === 0" class="empty-state">
                  <p>暂无进行中的对局</p>
                </div>
                <div v-else>
                  <div v-for="game in liveGames" :key="game.id" class="game-card">
                    <div class="game-players">
                      <span class="player-name black">{{ game.blackPlayer }}</span>
                      <span class="vs">VS</span>
                      <span class="player-name white">{{ game.whitePlayer }}</span>
                    </div>
                    <div class="game-info">
                      <span>{{ game.mode }}</span>
                      <span>{{ game.moveCount }}手</span>
                    </div>
                    <el-button type="primary" size="small" @click="watchGame(game.id)">
                      观战
                    </el-button>
                  </div>
                </div>
              </div>

              <div v-if="activeSpectateTab === 'replay'" class="replay-games">
                <div v-if="replayGames.length === 0" class="empty-state">
                  <p>暂无精彩回放</p>
                </div>
                <div v-else>
                  <div v-for="game in replayGames" :key="game.id" class="game-card">
                    <div class="game-players">
                      <span class="player-name">{{ game.blackPlayer }}</span>
                      <span class="vs">VS</span>
                      <span class="player-name">{{ game.whitePlayer }}</span>
                    </div>
                    <div class="game-info">
                      <span>{{ game.result }}</span>
                      <span>{{ game.duration }}</span>
                    </div>
                    <el-button type="success" size="small" @click="watchReplay(game.id)">
                      回放
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 对局记录面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'records' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>📜 对局记录</h2>
                <p>查看历史对局并进行复盘</p>
              </div>
            </div>
          </div>

          <div class="records-filter">
            <el-select v-model="recordsFilter" placeholder="筛选类型" @change="fetchRecords">
              <el-option label="全部" value="all" />
              <el-option label="胜局" value="win" />
              <el-option label="负局" value="loss" />
              <el-option label="平局" value="draw" />
            </el-select>
          </div>

          <div class="records-list">
            <div v-if="recordsLoading" class="loading-state">
              <div class="loading-spinner"></div>
              <p>加载中...</p>
            </div>

            <div v-else-if="gameRecords.length === 0" class="empty-state">
              <p>暂无对局记录</p>
            </div>

            <div v-else class="record-items">
              <div v-for="record in gameRecords" :key="record.id" class="record-card">
                <div class="record-result" :class="record.result.toLowerCase()">
                  {{ record.result === 'win' ? '胜' : record.result === 'loss' ? '负' : '平' }}
                </div>
                <div class="record-info">
                  <div class="record-opponent">{{ record.opponent }}</div>
                  <div class="record-meta">
                    <span>{{ record.mode }}</span>
                    <span>{{ record.date }}</span>
                  </div>
                </div>
                <div class="record-rating" :class="{ positive: record.ratingChange > 0, negative: record.ratingChange < 0 }">
                  {{ record.ratingChange > 0 ? '+' : '' }}{{ record.ratingChange }}
                </div>
                <el-button size="small" @click="viewReplay(record.id)">复盘</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 帮助中心面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'help' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>❓ 帮助中心</h2>
                <p>常见问题解答</p>
              </div>
            </div>
          </div>

          <div class="help-content">
            <div class="help-categories">
              <div
                v-for="category in helpCategories"
                :key="category.id"
                class="help-category-card"
                @click="activeHelpCategory = category.id"
              >
                <div class="category-icon">{{ category.icon }}</div>
                <div class="category-name">{{ category.name }}</div>
                <div class="category-desc">{{ category.description }}</div>
              </div>
            </div>

            <div class="help-articles">
              <h3>{{ getHelpCategoryName() }}</h3>
              <div v-if="filteredArticles.length === 0" class="empty-state">
                <p>暂无相关文章</p>
              </div>
              <div v-else>
                <div v-for="article in filteredArticles" :key="article.id" class="help-article-card">
                  <div class="article-title">{{ article.title }}</div>
                  <div class="article-preview">{{ article.preview }}</div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ========== 底部 Footer ========== -->
    <div class="page-footer">
      <canvas id="footerParticles" class="footer-particles"></canvas>
      <div class="footer-container">
        <!-- 主内容区 -->
        <div class="footer-main">
          <!-- Logo和描述 -->
          <div class="footer-brand">
            <div class="footer-logo">🎮</div>
            <div class="footer-brand-text">
              <h3>五子棋对战平台</h3>
              <p>与全球玩家实时对弈</p>
            </div>
          </div>

          <!-- 快速链接 -->
          <div class="footer-links">
            <div class="footer-link-section">
              <h4>游戏模式</h4>
              <a href="#" @click.prevent="handleNavClick(gameNavItems[0])">人机对战</a>
              <a href="#" @click.prevent="handleNavClick(gameNavItems[1])">快速匹配</a>
              <a href="#" @click.prevent="handleNavClick(gameNavItems[2])">房间对战</a>
            </div>
            <div class="footer-link-section">
              <h4>社区</h4>
              <a href="#" @click.prevent="handleNavClick(communityNavItems[1])">排行榜</a>
              <a href="#" @click.prevent="handleNavClick(communityNavItems[2])">对局记录</a>
              <a href="#" @click.prevent="handleNavClick(communityNavItems[0])">好友系统</a>
            </div>
            <div class="footer-link-section">
              <h4>帮助</h4>
              <a href="#" @click.prevent="handleNavClick(communityNavItems[3])">帮助中心</a>
              <a href="/rules">游戏规则</a>
              <a href="/about">关于我们</a>
            </div>
          </div>

          <!-- 统计信息 -->
          <div class="footer-stats">
            <div class="footer-stat-item">
              <div class="footer-stat-icon">👥</div>
              <div class="footer-stat-content">
                <div class="footer-stat-value">{{ onlineCount || '--' }}</div>
                <div class="footer-stat-label">在线玩家</div>
              </div>
            </div>
            <div class="footer-stat-item">
              <div class="footer-stat-icon">⚔️</div>
              <div class="footer-stat-content">
                <div class="footer-stat-value">{{ totalGames || '--' }}</div>
                <div class="footer-stat-label">今日对局</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 底部版权 -->
        <div class="footer-bottom">
          <div class="footer-copyright">
            © 2024 五子棋对战平台 · All rights reserved
          </div>
          <div class="footer-social">
            <a href="#" class="footer-social-link" title="GitHub">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- 点击外部关闭用户信息卡片 -->
    <div v-if="showUserInfoCard" class="overlay" @click="showUserInfoCard = false"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { wsClient } from '@/api/websocket'
import { get } from '@/api/http'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const horizontalNavScroll = ref<HTMLElement | null>(null)

// 监听路由变化，根据URL参数切换面板
watch(() => route.query.panel, (newPanel) => {
  console.log('[Home] Panel changed:', newPanel)
  if (newPanel && typeof newPanel === 'string') {
    const validPanels = ['pve', 'quick-match', 'room', 'puzzle', 'spectate', 'friends', 'rank', 'records', 'help']
    if (validPanels.includes(newPanel)) {
      activeNav.value = newPanel
    }
  }
})

// 监听路由变化，根据mode参数切换到快速匹配
watch(() => route.query.mode, (newMode) => {
  console.log('[Home] Mode changed:', newMode)
  if (newMode === 'casual' || newMode === 'ranked') {
    activeNav.value = 'quick-match'
  }
})

// 状态管理
const showUserInfoCard = ref(false)
const activeNav = ref<string | number>('pve') // 默认显示PVE
const publicRooms = ref<any[]>([])

const stats = ref({
  totalGames: 0,
  wins: 0,
  losses: 0,
  winRate: 0
})

const onlineCount = ref('--')
const totalGames = ref('--')

// 排行榜数据
const rankLoading = ref(false)
const activeRankTab = ref('all')
const rankList = ref<any[]>([])
const myRank = ref<number | null>(null)

// 好友系统数据
const activeFriendsTab = ref('list')
const friendSearchText = ref('')
const showAddFriendDialog = ref(false)
const friendsList = ref<any[]>([])
const pendingRequests = ref<any[]>([])
const playerSearchText = ref('')
const searchResults = ref<any[]>([])

const friendsTabs = [
  { label: '好友列表', value: 'list' },
  { label: '好友申请', value: 'requests' },
  { label: '查找玩家', value: 'search' }
]

const filteredFriends = computed(() => {
  if (!friendSearchText.value) return friendsList.value
  const search = friendSearchText.value.toLowerCase()
  return friendsList.value.filter(f =>
    f.username.toLowerCase().includes(search) ||
    (f.nickname && f.nickname.toLowerCase().includes(search))
  )
})

// 残局挑战数据
const puzzleDifficulty = ref('beginner')
const puzzleLoading = ref(false)
const puzzles = ref<any[]>([])
const puzzleCompletedCount = ref(0)
const puzzleTotalAttempted = ref(0)

const puzzleAccuracy = computed(() => {
  if (puzzleTotalAttempted.value === 0) return 0
  return Math.round((puzzleCompletedCount.value / puzzleTotalAttempted.value) * 100)
})

// 观战模式数据
const activeSpectateTab = ref('live')
const liveGames = ref<any[]>([])
const replayGames = ref<any[]>([])

const spectateTabs = [
  { label: '正在对战', value: 'live' },
  { label: '精彩回放', value: 'replay' }
]

// 对局记录数据
const recordsLoading = ref(false)
const recordsFilter = ref('all')
const gameRecords = ref<any[]>([])

// 帮助中心数据
const activeHelpCategory = ref('rules')
const helpCategories = [
  { id: 'rules', name: '游戏规则', icon: '📖', description: '了解五子棋基本规则' },
  { id: 'ranking', name: '积分系统', icon: '🏆', description: '段位与积分说明' },
  { id: 'matching', name: '匹配系统', icon: '⚡', description: '快速匹配与房间对战' },
  { id: 'account', name: '账户问题', icon: '👤', description: '登录、注册与设置' }
]

const helpArticles = [
  { id: 1, category: 'rules', title: '基本玩法', preview: '五子棋的目标是先于对手形成五子连线...' },
  { id: 2, category: 'rules', title: '禁手规则', preview: '在竞技模式中，黑方有禁手限制...' },
  { id: 3, category: 'ranking', title: '积分计算', preview: '采用ELO积分系统，根据对局结果和双方积分差计算...' },
  { id: 4, category: 'ranking', title: '段位系统', preview: '从入门到宗师，共7个段位...' },
  { id: 5, category: 'matching', title: '快速匹配', preview: '系统会根据你的积分自动匹配相近水平的对手...' },
  { id: 6, category: 'matching', title: '房间对战', preview: '创建或加入房间，与好友对战...' },
  { id: 7, category: 'account', title: '如何注册', preview: '点击注册按钮，填写用户名和密码即可...' },
  { id: 8, category: 'account', title: '修改资料', preview: '在个人资料页面可以修改昵称和头像...' }
]

const filteredArticles = computed(() => {
  return helpArticles.filter(a => a.category === activeHelpCategory.value)
})

const rankTabs = [
  { label: '总榜', value: 'all' },
  { label: '日榜', value: 'daily' },
  { label: '周榜', value: 'weekly' },
  { label: '月榜', value: 'monthly' }
]

// 获取排名样式
const getRankClass = (index: number): string => {
  if (index === 0) return 'first'
  if (index === 1) return 'second'
  if (index === 2) return 'third'
  return 'normal'
}

// 获取排行榜数据
const fetchRankList = async () => {
  rankLoading.value = true
  try {
    const response = await get(`/rank/${activeRankTab.value}`)

    if (response.data && response.data.code === 200) {
      const list = response.data.data || []
      rankList.value = list.map((player: any) => ({
        ...player,
        isMe: player.id === userStore.userInfo?.id
      }))
      myRank.value = response.data.myRank || 0
    } else {
      throw new Error(response.data?.message || '获取失败')
    }
  } catch (error) {
    console.error('[Home] 获取排行榜失败:', error)

    // 失败时显示当前用户
    if (userStore.userInfo) {
      rankList.value = [{
        ...userStore.userInfo,
        isMe: true,
        winRate: 0,
        totalGames: 0
      }]
    }
  } finally {
    rankLoading.value = false
  }
}

// 好友系统函数
const fetchFriends = async () => {
  try {
    // TODO: 调用实际的API
    friendsList.value = []
  } catch (error) {
    console.error('[Home] 获取好友列表失败:', error)
  }
}

const acceptRequest = (id: number) => {
  ElMessage.success('已接受好友申请')
  pendingRequests.value = pendingRequests.value.filter(r => r.id !== id)
}

const rejectRequest = (id: number) => {
  ElMessage.info('已拒绝好友申请')
  pendingRequests.value = pendingRequests.value.filter(r => r.id !== id)
}

const searchPlayer = () => {
  if (!playerSearchText.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  // TODO: 实际搜索API
  ElMessage.success('搜索功能开发中...')
}

const sendFriendRequest = (player: any) => {
  showAddFriendDialog.value = true
  ElMessage.info('添加好友功能开发中...')
}

// 残局挑战函数
const fetchPuzzles = async () => {
  puzzleLoading.value = true
  try {
    // TODO: 调用实际的残局API
    puzzles.value = []
  } catch (error) {
    console.error('[Home] 获取残局失败:', error)
  } finally {
    puzzleLoading.value = false
  }
}

const startPuzzle = (puzzle: any) => {
  ElMessage.info('残局挑战功能开发中...')
}

// 观战模式函数
const watchGame = (gameId: string) => {
  ElMessage.info('观战功能开发中...')
}

const watchReplay = (gameId: string) => {
  ElMessage.info('回放功能开发中...')
}

// 对局记录函数
const fetchRecords = async () => {
  recordsLoading.value = true
  try {
    // TODO: 调用实际的记录API
    gameRecords.value = []
  } catch (error) {
    console.error('[Home] 获取对局记录失败:', error)
  } finally {
    recordsLoading.value = false
  }
}

const viewReplay = (recordId: string) => {
  ElMessage.info('复盘功能开发中...')
}

// 帮助中心函数
const getHelpCategoryName = () => {
  const category = helpCategories.find(c => c.id === activeHelpCategory.value)
  return category?.name || ''
}

// 导航项
const gameNavItems = [
  { id: 'pve', name: '人机对战', icon: '🤖' },
  { id: 'quick-match', name: '快速匹配', icon: '⚡' },
  { id: 'room', name: '房间对战', icon: '🏠' },
  { id: 'puzzle', name: '残局闯关', icon: '📚' },
  { id: 'spectate', name: '观战模式', icon: '👀' }
]

const communityNavItems = [
  { id: 'friends', name: '好友系统', icon: '👫' },
  { id: 'rank', name: '排行榜', icon: '🥇' },
  { id: 'records', name: '对局记录', icon: '📜' },
  { id: 'help', name: '帮助中心', icon: '❓' }
]

const navItems = computed(() => [...gameNavItems, ...communityNavItems])

// 切换用户信息卡片
const toggleUserInfoCard = () => {
  showUserInfoCard.value = !showUserInfoCard.value
}

// 获取段位名称
const getLevelName = (level: number): string => {
  const levels = ['', '入门', '初级', '中级', '高级', '大师', '宗师']
  return levels[Math.min(level, 6)] || '宗师'
}

// 导航点击处理
const handleNavClick = (item: any) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 跳转到带参数的首页
  if (item.id) {
    router.push({ path: '/home', query: { panel: item.id } })
  } else if (item.path) {
    router.push(item.path)
  } else if (item.id === 'puzzle') {
    router.push({ path: '/home', query: { panel: 'puzzle' } })
  } else if (item.id === 'spectate') {
    ElMessage.info('观战功能开发中...')
  }
}

// 开始人机对战
const startPVE = (difficulty: string) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  // 跳转到PVE游戏页面
  router.push(`/pve?difficulty=${difficulty}`)
}

// 快速匹配
const handleStartMatch = async (mode: string) => {
  console.log('[Home] handleStartMatch 被调用，mode:', mode)
  console.log('[Home] 用户登录状态:', userStore.isLoggedIn)

  if (!userStore.isLoggedIn) {
    console.log('[Home] 用户未登录，跳转到登录页')
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  console.log('[Home] 准备跳转到匹配页面')
  console.log('[Home] 目标 URL:', `/match?mode=${mode}`)

  try {
    await router.push(`/match?mode=${mode}`)
    console.log('[Home] 路由跳转成功')
  } catch (error) {
    console.error('[Home] 路由跳转失败:', error)
  }
}

// 创建房间
const createRoom = () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  ElMessage.info('创建房间功能开发中...')
}

// 加入房间
const joinRoom = () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push('/room')
}

// 加载公开房间
const loadPublicRooms = async () => {
  // TODO: 等待后端实现房间列表API
  // 暂时使用模拟数据
  publicRooms.value = []
  /* API实现后启用：
  try {
    const response = await get('/rooms/public')
    if (response.data?.code === 200) {
      publicRooms.value = response.data.data.rooms || []
    }
  } catch (error) {
    console.error('加载房间列表失败:', error)
  }
  */
}

// 加入指定房间
const joinRoomById = (roomId: string) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push(`/game/${roomId}`)
}

// 获取用户统计
const fetchUserStats = async () => {
  try {
    if (!userStore.userInfo?.id) return

    const response = await get('/user/stats', {
      params: { userId: userStore.userInfo.id }
    })

    if (response.data?.code === 200) {
      const data = response.data.data
      stats.value = {
        totalGames: data.totalGames || 0,
        wins: data.wins || 0,
        losses: data.losses || 0,
        winRate: data.winRate || 0
      }
    }
  } catch (error) {
    console.error('获取用户统计失败:', error)
  }
}

// 退出登录
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning'
    })

    userStore.logout()
    showUserInfoCard.value = false
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 用户取消
  }
}

// 去个人中心
const goToProfile = () => {
  showUserInfoCard.value = false
  router.push('/profile')
}

// 横向导航滚动
const scrollHorizontalNav = (amount: number) => {
  if (horizontalNavScroll.value) {
    horizontalNavScroll.value.scrollBy({
      left: amount,
      behavior: 'smooth'
    })
  }
}

onMounted(() => {
  console.log('[Home] onMounted - URL panel:', route.query.panel)
  console.log('[Home] onMounted - URL mode:', route.query.mode)
  console.log('[Home] onMounted - 初始 activeNav:', activeNav.value)

  // 检查 URL 参数，初始化正确的面板
  const panel = route.query.panel as string
  const mode = route.query.mode as string

  if (panel) {
    const validPanels = ['pve', 'quick-match', 'room', 'puzzle', 'spectate', 'friends', 'rank', 'records', 'help']
    if (validPanels.includes(panel)) {
      activeNav.value = panel
      console.log('[Home] 根据 URL 参数设置 activeNav:', panel)
    }
  } else if (mode === 'casual' || mode === 'ranked') {
    activeNav.value = 'quick-match'
    console.log('[Home] 根据 mode 参数设置 activeNav 为 quick-match')
  }

  console.log('[Home] onMounted - 最终 activeNav:', activeNav.value)

  // 只在用户登录时获取统计数据
  if (userStore.isLoggedIn && userStore.userInfo?.id) {
    fetchUserStats()
  }

  // 加载排行榜数据
  fetchRankList()

  // 加载好友数据
  fetchFriends()

  // 加载残局数据
  fetchPuzzles()

  // 加载对局记录
  fetchRecords()

  // 加载房间列表（暂时禁用，等待后端API实现）
  // loadPublicRooms()

  // 初始化粒子效果
  initParticleEffects()

  // 初始化横向导航的触摸滚动
  initHorizontalNavScroll()
})

// 初始化横向导航的触摸滚动功能
const initHorizontalNavScroll = () => {
  const scrollContainer = horizontalNavScroll.value
  if (!scrollContainer) return

  let isDown = false
  let startX = 0
  let scrollLeft = 0

  // 鼠标事件（桌面端）
  scrollContainer.addEventListener('mousedown', (e: MouseEvent) => {
    // 如果点击的是导航项，不触发拖动
    if ((e.target as HTMLElement).closest('.horizontal-nav-item')) {
      return
    }
    isDown = true
    startX = e.pageX - scrollContainer.offsetLeft
    scrollLeft = scrollContainer.scrollLeft
    scrollContainer.style.cursor = 'grabbing'
  })

  scrollContainer.addEventListener('mouseleave', () => {
    isDown = false
    scrollContainer.style.cursor = ''
  })

  scrollContainer.addEventListener('mouseup', () => {
    isDown = false
    scrollContainer.style.cursor = ''
  })

  scrollContainer.addEventListener('mousemove', (e: MouseEvent) => {
    if (!isDown) return
    e.preventDefault()
    const x = e.pageX - scrollContainer.offsetLeft
    const walk = (x - startX) * 2 // 滚动速度
    scrollContainer.scrollLeft = scrollLeft - walk
  })

  // 触摸事件（移动端）- 增强触摸滚动
  let touchStartX = 0
  let touchStartY = 0

  scrollContainer.addEventListener('touchstart', (e: TouchEvent) => {
    touchStartX = e.touches[0].clientX
    touchStartY = e.touches[0].clientY
  }, { passive: true })

  scrollContainer.addEventListener('touchmove', (e: TouchEvent) => {
    const touchEndX = e.touches[0].clientX
    const touchEndY = e.touches[0].clientY

    const diffX = touchStartX - touchEndX
    const diffY = touchStartY - touchEndY

    // 如果水平滑动距离大于垂直滑动距离，阻止默认行为
    if (Math.abs(diffX) > Math.abs(diffY)) {
      // 只在导航容器内阻止默认行为
      const target = e.target as HTMLElement
      if (scrollContainer.contains(target) && !target.closest('.horizontal-nav-item')) {
        e.preventDefault()
      }
    }
  }, { passive: false })

  // 监听滚动事件，显示/隐藏拖拽手柄
  scrollContainer.addEventListener('scroll', () => {
    updateDragHandlesVisibility()
  })

  // 初始化拖拽手柄显示状态
  updateDragHandlesVisibility()
}

// 更新拖拽手柄的可见性
const updateDragHandlesVisibility = () => {
  const scrollContainer = horizontalNavScroll.value
  if (!scrollContainer) return

  const leftHandle = document.querySelector('.drag-handle-left') as HTMLElement
  const rightHandle = document.querySelector('.drag-handle-right') as HTMLElement

  const canScrollLeft = scrollContainer.scrollLeft > 0
  const canScrollRight = scrollContainer.scrollLeft < scrollContainer.scrollWidth - scrollContainer.clientWidth

  if (leftHandle) {
    leftHandle.style.opacity = canScrollLeft ? '1' : '0.3'
    leftHandle.style.pointerEvents = canScrollLeft ? 'auto' : 'none'
  }

  if (rightHandle) {
    rightHandle.style.opacity = canScrollRight ? '1' : '0.3'
    rightHandle.style.pointerEvents = canScrollRight ? 'auto' : 'none'
  }
}

// 粒子效果类
class ParticleEffect {
  private canvas: HTMLCanvasElement | null = null
  private ctx: CanvasRenderingContext2D | null = null
  private particles: any[] = []
  private particleCount: number
  private speed: number
  private size: { min: number; max: number }
  private color: string
  private animationId: number | null = null

  constructor(canvasId: string, options: any = {}) {
    this.canvas = document.getElementById(canvasId) as HTMLCanvasElement
    if (!this.canvas) return

    this.ctx = this.canvas.getContext('2d')
    if (!this.ctx) return

    this.particles = []
    this.particleCount = options.particleCount || 30
    this.speed = options.speed || 0.5
    this.size = options.size || { min: 2, max: 5 }
    this.color = options.color || 'rgba(255, 255, 255, 0.6)'

    this.resize()
    this.init()
    this.animate()

    window.addEventListener('resize', () => this.resize())
  }

  resize() {
    if (!this.canvas || !this.canvas.parentElement) return
    const rect = this.canvas.parentElement.getBoundingClientRect()
    this.canvas.width = rect.width
    this.canvas.height = rect.height
  }

  init() {
    for (let i = 0; i < this.particleCount; i++) {
      this.particles.push(this.createParticle())
    }
  }

  createParticle() {
    if (!this.canvas) return {}
    return {
      x: Math.random() * this.canvas.width,
      y: Math.random() * this.canvas.height,
      size: Math.random() * (this.size.max - this.size.min) + this.size.min,
      speedX: (Math.random() - 0.5) * this.speed,
      speedY: (Math.random() - 0.5) * this.speed,
      opacity: Math.random() * 0.5 + 0.2
    }
  }

  update() {
    if (!this.canvas) return
    this.particles.forEach((p: any) => {
      p.x += p.speedX
      p.y += p.speedY

      if (p.x < 0) p.x = this.canvas!.width
      if (p.x > this.canvas!.width) p.x = 0
      if (p.y < 0) p.y = this.canvas!.height
      if (p.y > this.canvas!.height) p.y = 0
    })
  }

  draw() {
    if (!this.ctx || !this.canvas) return
    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height)

    this.particles.forEach((p: any) => {
      this.ctx!.beginPath()
      this.ctx!.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      this.ctx!.fillStyle = this.color.replace('0.6', p.opacity.toFixed(2))
      this.ctx!.fill()
    })

    // 连线效果
    this.particles.forEach((p1: any, i: number) => {
      this.particles.slice(i + 1).forEach((p2: any) => {
        const dx = p1.x - p2.x
        const dy = p1.y - p2.y
        const distance = Math.sqrt(dx * dx + dy * dy)

        if (distance < 100) {
          this.ctx!.beginPath()
          this.ctx!.strokeStyle = this.color.replace('0.6', (0.15 * (1 - distance / 100)).toFixed(2))
          this.ctx!.lineWidth = 0.5
          this.ctx!.moveTo(p1.x, p1.y)
          this.ctx!.lineTo(p2.x, p2.y)
          this.ctx!.stroke()
        }
      })
    })
  }

  animate() {
    this.update()
    this.draw()
    this.animationId = requestAnimationFrame(() => this.animate())
  }

  destroy() {
    if (this.animationId) {
      cancelAnimationFrame(this.animationId)
    }
    window.removeEventListener('resize', () => this.resize())
  }
}

// 存储粒子效果实例
const particleEffects: ParticleEffect[] = []

// 初始化粒子效果
const initParticleEffects = () => {
  // 页眉粒子效果 (白色粒子)
  const headerEffect = new ParticleEffect('headerParticles', {
    particleCount: 25,
    speed: 0.4,
    size: { min: 2, max: 4 },
    color: 'rgba(255, 255, 255, 0.6)'
  })
  if (headerEffect) particleEffects.push(headerEffect)

  // 页尾粒子效果 (橙色粒子)
  const footerEffect = new ParticleEffect('footerParticles', {
    particleCount: 20,
    speed: 0.3,
    size: { min: 2, max: 4 },
    color: 'rgba(255, 107, 53, 0.4)'
  })
  if (footerEffect) particleEffects.push(footerEffect)
}

onUnmounted(() => {
  // 清理粒子效果
  particleEffects.forEach(effect => effect.destroy())
  particleEffects.length = 0

  // 清理横向导航滚动事件监听器
  if (horizontalNavScroll.value) {
    // 克隆节点来移除所有事件监听器
    const newElement = horizontalNavScroll.value.cloneNode(true)
    horizontalNavScroll.value.parentNode?.replaceChild(newElement, horizontalNavScroll.value)
  }
})
</script>

<style scoped>
/* ========== 全局样式 ========== */
.home-view {
  min-height: 100vh;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 50%, #ffd4c4 100%);
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
}

/* ========== 顶部 Header ========== */
.header {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 50%, #ffa07a 100%);
  padding: 20px 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(255, 107, 53, 0.3);
}

.header-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0) translateX(0);
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    transform: translateY(-100px) translateX(50px);
    opacity: 0;
  }
}

.header > *:not(.header-particles):not(.user-info-card) {
  position: relative;
  z-index: 2;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.logo-large {
  width: 80px;
  height: 80px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-large svg {
  width: 50px;
  height: 50px;
}

.header-title h1 {
  font-size: 28px;
  font-weight: bold;
  color: white;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  margin-bottom: 5px;
}

.header-title p {
  font-size: 14px;
  opacity: 0.95;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 15px;
}

.user-profile-display {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 25px;
  cursor: pointer;
  transition: all 0.3s;
}

.user-profile-display:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-2px);
}

.header-avatar-small {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  color: white;
}

.header-user-name {
  font-size: 14px;
  font-weight: 600;
  color: white;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-user-level {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.8);
  background: rgba(255, 107, 53, 0.3);
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 600;
}

.header-icons {
  display: flex;
  gap: 10px;
}

.header-icon {
  width: 42px;
  height: 42px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s;
  font-size: 18px;
}

.header-icon:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.1);
}

/* ========== 用户信息卡片 ========== */
.user-info-card {
  position: fixed;
  top: 110px;
  right: 30px;
  z-index: 1000;
  display: none;
  animation: fadeIn 0.3s ease;
}

.user-info-card.active {
  display: block;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(-10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.user-info-card-content {
  background: white;
  border-radius: 20px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.15);
  min-width: 320px;
  overflow: hidden;
}

.user-info-header {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 15px;
  position: relative;
}

.user-info-avatar {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: bold;
  color: white;
  border: 3px solid rgba(255, 255, 255, 0.5);
}

.user-info-names {
  flex: 1;
  color: white;
}

.user-info-nickname {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 4px;
}

.user-info-username {
  font-size: 14px;
  opacity: 0.9;
}

.user-info-close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  font-size: 18px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.user-info-close:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.1);
}

.user-info-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: #f0f0f0;
  padding: 1px;
}

.stat-item {
  background: white;
  padding: 10px;
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 5px;
}

.stat-value {
  font-size: 18px;
  font-weight: bold;
  color: #ff6b35;
}

.user-info-actions {
  padding: 15px;
  display: flex;
  gap: 10px;
}

.user-info-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.user-info-btn.primary {
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
}

.user-info-btn.primary:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(255, 107, 53, 0.4);
}

.user-info-btn.secondary {
  background: #f5f5f5;
  color: #666;
}

.user-info-btn.secondary:hover {
  background: #eee;
}

.overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: transparent;
}

/* ========== 简化版用户信息卡片 ========== */
.user-info-card-simple .user-info-card-content {
  min-width: 280px;
}

.user-info-header-simple {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
}

.user-info-names-simple {
  flex: 1;
  color: white;
}

.user-info-nickname-simple {
  font-size: 20px;
  font-weight: bold;
  text-align: center;
}

.user-info-card-simple .user-info-actions {
  padding: 15px;
}

/* ========== 横向滚动导航 ========== */
.horizontal-nav {
  display: none;
  background: white;
  border-bottom: 1px solid #f0f0f0;
  padding: 10px 0;
  position: sticky;
  top: 0;
  z-index: 100;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

/* 拖拽手柄 */
.drag-handle {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 32px;
  height: 32px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 10;
  cursor: grab;
  color: #ff6b35;
  font-size: 16px;
  transition: all 0.3s ease;
  pointer-events: auto;
}

.drag-handle:active {
  cursor: grabbing;
  transform: translateY(-50%) scale(0.9);
}

.drag-handle-left {
  left: 8px;
}

.drag-handle-right {
  right: 8px;
}

.drag-handle svg {
  width: 20px;
  height: 20px;
}

/* 拖拽手柄渐隐效果 */
.horizontal-nav-scroll:not(:hover) ~ .drag-handle {
  opacity: 0.6;
}

.horizontal-nav-scroll:hover ~ .drag-handle {
  opacity: 1;
}

/* 渐变遮罩效果 - 提示可以滚动 */
.horizontal-nav::before,
.horizontal-nav::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  width: 30px;
  pointer-events: none;
  z-index: 5;
  transition: opacity 0.3s ease;
}

.horizontal-nav::before {
  left: 0;
  background: linear-gradient(to right, white, transparent);
}

.horizontal-nav::after {
  right: 0;
  background: linear-gradient(to left, white, transparent);
}

/* 当滚动到最左侧时隐藏左侧遮罩 */
.horizontal-nav.scrolled-to-start::before {
  opacity: 0;
}

/* 当滚动到最右侧时隐藏右侧遮罩 */
.horizontal-nav.scrolled-to-end::after {
  opacity: 0;
}

.horizontal-nav-scroll {
  display: flex;
  flex-wrap: nowrap;
  overflow-x: auto;
  overflow-y: hidden;
  gap: 8px;
  padding: 0 50px; /* 增加左右内边距，给拖拽手柄留空间 */
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  -ms-overflow-style: none;
  scrollbar-width: none;
  width: 100%;
  touch-action: pan-x pinch-zoom;
  cursor: grab;
  position: relative;
}

.horizontal-nav-scroll:active {
  cursor: grabbing;
}

.horizontal-nav-scroll::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.horizontal-nav-item {
  flex: 0 0 auto;
  padding: 10px 18px;
  border-radius: 20px;
  font-size: 14px;
  color: #666;
  background: #f5f5f5;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  min-width: fit-content;
  user-select: none;
  -webkit-user-select: none;
}

.horizontal-nav-item.active {
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
  box-shadow: 0 2px 10px rgba(255, 107, 53, 0.3);
}

.horizontal-nav-item:active {
  transform: scale(0.95);
}

.horizontal-nav-icon {
  font-size: 16px;
}

/* ========== 主容器 ========== */
.main-container {
  display: flex;
  min-height: calc(100vh - 120px);
}

/* ========== 侧边栏 ========== */
.sidebar {
  width: 240px;
  background: white;
  padding: 25px 20px;
  box-shadow: 2px 0 20px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 12px;
  color: #999;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 15px;
  padding-left: 10px;
}

.nav-section {
  margin-bottom: 25px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px 18px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 8px;
  color: #666;
  font-size: 15px;
}

.nav-item:hover {
  background: #fff5f0;
  color: #ff6b35;
  transform: translateX(5px);
}

.nav-item.active {
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
  box-shadow: 0 4px 15px rgba(255, 107, 53, 0.3);
}

.nav-item.active:hover {
  transform: translateX(0);
}

.nav-icon {
  font-size: 22px;
  width: 30px;
  text-align: center;
}

.nav-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 20px 0;
}

/* ========== 内容区 ========== */
.content-area {
  flex: 1;
  padding: 30px 40px;
  overflow-y: auto;
}

.content-panel {
  display: none;
}

.content-panel.active {
  display: block;
}

/* ========== Hero Banner ========== */
.hero-banner {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  border-radius: 24px;
  padding: 50px 40px;
  margin-bottom: 35px;
  position: relative;
  overflow: hidden;
  box-shadow: 0 15px 50px rgba(255, 107, 53, 0.3);
}

.hero-banner-online {
  background: linear-gradient(90deg, #1e3c72 0%, #2a5298 50%, #1e3c72 100%);
  box-shadow: 0 15px 50px rgba(30, 60, 114, 0.3);
}

.hero-banner-online .hero-text h2,
.hero-banner-online .hero-text p {
  color: white;
}

.hero-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: relative;
  z-index: 2;
}

.hero-text {
  color: white;
  flex: 1;
}

.hero-text h2 {
  font-size: 36px;
  font-weight: bold;
  margin-bottom: 12px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.2);
}

.hero-text p {
  font-size: 18px;
  opacity: 0.95;
}

.hero-animation {
  flex: 0 0 200px;
}

.board-preview {
  width: 200px;
  height: 200px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 16px;
  position: relative;
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  grid-template-rows: repeat(15, 1fr);
  gap: 1px;
  padding: 10px;
  backdrop-filter: blur(10px);
}

.preview-line {
  position: absolute;
  background: rgba(255, 255, 255, 0.3);
}

.preview-line:nth-child(1) {
  width: 100%;
  height: 1px;
  top: 50%;
}

.preview-line:nth-child(2) {
  width: 1px;
  height: 100%;
  left: 50%;
}

.preview-line:nth-child(3) {
  width: 70%;
  height: 1px;
  top: 35%;
  left: 15%;
}

.preview-line:nth-child(4) {
  width: 1px;
  height: 70%;
  top: 15%;
  left: 65%;
}

.preview-line:nth-child(5) {
  width: 70%;
  height: 1px;
  top: 65%;
  left: 15%;
}

.piece-anim {
  position: absolute;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  animation: placePiece 4s infinite;
}

.piece-anim:nth-child(6) {
  background: radial-gradient(circle at 30% 30%, #444, #000);
  top: 30%;
  left: 30%;
}

.piece-anim:nth-child(7) {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
  top: 30%;
  left: 50%;
}

.piece-anim:nth-child(8) {
  background: radial-gradient(circle at 30% 30%, #444, #000);
  top: 50%;
  left: 40%;
}

.piece-anim:nth-child(9) {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
  top: 50%;
  left: 60%;
}

@keyframes placePiece {
  0% {
    transform: scale(0);
    opacity: 0;
  }
  20% {
    transform: scale(1);
    opacity: 1;
  }
  80% {
    transform: scale(1);
    opacity: 1;
  }
  100% {
    transform: scale(0);
    opacity: 0;
  }
}

/* ========== PVE 难度卡片 ========== */
.pve-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.pve-card {
  background: white;
  border-radius: 16px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
  border: 3px solid transparent;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.pve-card:hover {
  border-color: #ff8c61;
  transform: translateY(-5px);
  box-shadow: 0 8px 30px rgba(255, 107, 53, 0.2);
}

.diff-icon-large {
  font-size: 64px;
  margin-bottom: 35px;
}

.diff-title {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  margin-bottom: 8px;
}

.diff-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 15px;
}

.diff-stars-large {
  font-size: 24px;
}

.star {
  color: #ddd;
}

.star.filled {
  color: #ffd700;
}

/* ========== 游戏规则 ========== */
.pve-rules-section {
  background: white;
  border-radius: 16px;
  padding: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.pve-rules-section h3 {
  font-size: 18px;
  color: #ff6b35;
  margin-bottom: 15px;
}

.pve-rules-section ul {
  list-style: none;
  padding: 0;
}

.pve-rules-section li {
  padding: 10px 0;
  padding-left: 25px;
  position: relative;
  color: #666;
}

.pve-rules-section li::before {
  content: '✓';
  position: absolute;
  left: 0;
  color: #67c23a;
  font-weight: bold;
}

/* ========== PVE 游戏区域 ========== */
.pve-game-area {
  background: white;
  border-radius: 16px;
  padding: 30px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.pve-game-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  padding: 15px 20px;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border-radius: 12px;
}

.pve-player-info {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  border-radius: 10px;
  transition: all 0.3s;
}

.pve-player-info.active {
  background: rgba(255, 107, 53, 0.2);
  box-shadow: 0 0 15px rgba(255, 107, 53, 0.3);
}

.pve-avatar-small {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
  font-size: 16px;
}

.pve-player-details {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pve-player-name {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.pve-player-color {
  font-size: 12px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 4px;
}

.pve-piece-icon {
  font-size: 14px;
}

.pve-vs {
  font-size: 18px;
  font-weight: bold;
  color: #999;
}

.pve-game-status {
  text-align: center;
  margin-bottom: 20px;
}

.pve-status-tag {
  display: inline-block;
  padding: 8px 24px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
}

.pve-status-turn {
  background: linear-gradient(135deg, #67c23a, #85ce61);
  color: white;
}

.pve-status-thinking {
  background: linear-gradient(135deg, #e6a23c, #f0c78a);
  color: white;
}

/* PVE 棋盘 */
.pve-board-container {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.pve-board {
  width: 400px;
  height: 400px;
  background: linear-gradient(135deg, #f5e6d3 0%, #e8d5c4 100%);
  border-radius: 8px;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  display: grid;
  grid-template-columns: repeat(15, 1fr);
  grid-template-rows: repeat(15, 1fr);
  padding: 12px;
}

.pve-row {
  display: contents;
}

.pve-cell {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.pve-cell::before,
.pve-cell::after {
  content: '';
  position: absolute;
  background: #8b7355;
}

.pve-cell::before {
  width: 100%;
  height: 1px;
  top: 50%;
}

.pve-cell::after {
  width: 1px;
  height: 100%;
  left: 50%;
}

/* 边界处理 */
.pve-cell:nth-child(-n+15)::after {
  top: 50%;
  height: 50%;
}

.pve-cell:nth-child(n+211)::after {
  height: 50%;
}

.pve-cell:nth-child(15n+1)::before {
  left: 50%;
  width: 50%;
}

.pve-cell:nth-child(15n)::before {
  width: 50%;
}

.pve-star-point {
  width: 6px;
  height: 6px;
  background: #8b7355;
  border-radius: 50%;
  position: absolute;
  z-index: 1;
}

.pve-piece {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  z-index: 2;
  box-shadow: 1px 1px 3px rgba(0, 0, 0, 0.3);
  position: relative;
}

.pve-piece.black {
  background: radial-gradient(circle at 30% 30%, #666, #000);
}

.pve-piece.white {
  background: radial-gradient(circle at 30% 30%, #fff, #ddd);
}

.pve-last-move {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 6px;
  height: 6px;
  background: #ff6b35;
  border-radius: 50%;
}

/* PVE 控制按钮 */
.pve-controls {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.pve-btn {
  padding: 10px 20px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  background: linear-gradient(135deg, #f5f5f5, #e8e8e8);
  color: #333;
  display: flex;
  align-items: center;
  gap: 6px;
}

.pve-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.pve-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pve-btn-danger {
  background: linear-gradient(135deg, #f56c6c, #f89898);
  color: white;
}

.pve-btn-warning {
  background: linear-gradient(135deg, #e6a23c, #f0c78a);
  color: white;
}

/* ========== 匹配模式 ========== */
.match-modes {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.match-mode-card {
  background: white;
  border-radius: 16px;
  padding: 30px;
  text-align: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  transition: all 0.3s;
  cursor: pointer;
  position: relative;
  z-index: 2;
}

.match-mode-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
}

.match-mode-card.primary {
  background: white;
}

.match-mode-card.primary .match-mode-title,
.match-mode-card.primary .match-mode-desc {
  color: #333;
}

.match-mode-icon {
  font-size: 48px;
  margin-bottom: 15px;
}

.match-mode-title {
  font-size: 20px;
  font-weight: bold;
  color: #333;
  margin-bottom: 8px;
}

.match-mode-desc {
  font-size: 14px;
  color: #999;
  margin-bottom: 20px;
}

.match-mode-btn {
  margin-top: 15px;
  padding: 8px 20px;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  display: inline-block;
  transition: all 0.3s;
}

.match-mode-card.primary .match-mode-btn {
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
}

.match-mode-card:hover .match-mode-btn {
  transform: scale(1.05);
}

.match-info {
  background: white;
  border-radius: 16px;
  padding: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.match-info h3 {
  font-size: 18px;
  color: #ff6b35;
  margin-bottom: 15px;
}

.match-info ul {
  list-style: none;
  padding: 0;
}

.match-info li {
  padding: 10px 0;
  padding-left: 25px;
  position: relative;
  color: #666;
}

.match-info li::before {
  content: '💡';
  position: absolute;
  left: 0;
}

/* ========== 房间操作 ========== */
.room-actions {
  display: flex;
  gap: 15px;
  margin-bottom: 30px;
}

.room-btn {
  flex: 1;
  padding: 20px;
  border: none;
  border-radius: 16px;
  font-size: 18px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s;
  background: white;
  color: #333;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.room-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.12);
}

.room-btn.primary {
  background: linear-gradient(135deg, #f093fb, #f5576c);
  color: white;
}

.room-btn-icon {
  font-size: 24px;
}

.room-list {
  background: white;
  border-radius: 16px;
  padding: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.room-list h3 {
  font-size: 18px;
  color: #ff6b35;
  margin-bottom: 20px;
}

.empty-state {
  text-align: center;
  padding: 40px;
  color: #999;
}

.refresh-btn {
  margin-top: 15px;
  padding: 10px 25px;
  border: none;
  border-radius: 20px;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
  cursor: pointer;
  font-weight: 600;
}

.room-list-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.room-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px 20px;
  background: #f8f8f8;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.room-item:hover {
  background: linear-gradient(135deg, #fff5f0, #ffe8dc);
}

.room-name {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
}

.room-meta {
  font-size: 12px;
  color: #999;
}

.join-mini-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 15px;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
  cursor: pointer;
  font-weight: 600;
}

/* ========== 排行榜 ========== */
.rank-tabs {
  display: flex;
  gap: 10px;
  margin: 30px 0;
  border-bottom: 2px solid #f0f0f0;
}

.rank-tab-item {
  padding: 12px 24px;
  font-size: 16px;
  color: #666;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  transition: all 0.3s;
}

.rank-tab-item:hover {
  color: #ff6b35;
}

.rank-tab-item.active {
  color: #ff6b35;
  border-bottom-color: #ff6b35;
  font-weight: 500;
}

.rank-content {
  min-height: 400px;
}

.rank-items {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.rank-item {
  display: flex;
  align-items: center;
  padding: 20px;
  background: #fafafa;
  border-radius: 12px;
  transition: all 0.3s;
}

.rank-item:hover {
  background: #fff5f0;
  transform: translateX(5px);
}

.rank-item.is-me {
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border: 2px solid #ff6b35;
}

.rank-number {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: bold;
  color: #999;
  margin-right: 15px;
}

.rank-number.rank-first {
  color: #ffd700;
  font-size: 24px;
}

.rank-number.rank-second {
  color: #c0c0c0;
}

.rank-number.rank-third {
  color: #cd7f32;
}

.medal {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.player-avatar {
  margin-right: 15px;
}

.avatar-circle {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
}

.player-info {
  flex: 1;
}

.player-name {
  font-size: 18px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.me-tag {
  background: #ff6b35;
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.online-tag {
  background: #67c23a;
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.player-level {
  font-size: 14px;
  color: #999;
}

.player-stats {
  display: flex;
  gap: 25px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #999;
  display: block;
  margin-bottom: 3px;
}

.stat-value {
  font-size: 20px;
  font-weight: bold;
  color: #ff6b35;
}

.my-rank-banner {
  margin-top: 30px;
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  color: white;
  padding: 15px 30px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 15px;
}

.my-rank-label {
  font-size: 14px;
}

.my-rank-value {
  font-size: 20px;
  font-weight: bold;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #f0f0f0;
  border-top-color: #ff6b35;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 15px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ========== 好友系统 ========== */
.friends-tabs {
  display: flex;
  gap: 10px;
  margin: 30px 0;
  border-bottom: 2px solid #f0f0f0;
}

.friends-tab-item {
  padding: 12px 24px;
  font-size: 16px;
  color: #666;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  transition: all 0.3s;
}

.friends-tab-item:hover {
  color: #667eea;
}

.friends-tab-item.active {
  color: #667eea;
  border-bottom-color: #667eea;
  font-weight: 500;
}

.friends-content {
  min-height: 400px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.friend-items {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.friend-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
}

.friend-avatar-circle {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  font-weight: bold;
}

.friend-details {
  flex: 1;
}

.friend-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.friend-level {
  font-size: 14px;
  color: #999;
}

.friend-status {
  padding: 5px 12px;
  border-radius: 15px;
  font-size: 12px;
  background: #e0e0e0;
  color: #999;
}

.friend-status.online {
  background: #67c23a;
  color: white;
}

.request-items {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.request-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
}

.request-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
}

.request-info {
  flex: 1;
}

.request-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.request-message {
  font-size: 14px;
  color: #666;
}

.search-results {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-top: 20px;
}

.player-result-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
}

.player-avatar {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
}

/* ========== 残局挑战 ========== */
.puzzle-difficulty-selector {
  display: flex;
  justify-content: center;
  margin: 30px 0;
}

.puzzle-stats {
  display: flex;
  justify-content: center;
  gap: 30px;
  margin-bottom: 20px;
  color: #666;
}

.puzzle-items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 15px;
}

.puzzle-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.puzzle-card:hover {
  background: #fff5f0;
  transform: translateY(-2px);
}

.puzzle-card.completed {
  background: #f0f9ff;
  border: 2px solid #67c23a;
}

.puzzle-card.failed {
  background: #fef0f0;
}

.puzzle-number {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
}

.puzzle-info {
  flex: 1;
}

.puzzle-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.puzzle-meta {
  font-size: 12px;
  color: #999;
}

.puzzle-meta span {
  margin-right: 10px;
}

/* ========== 观战模式 ========== */
.spectate-tabs {
  display: flex;
  gap: 10px;
  margin: 30px 0;
  border-bottom: 2px solid #f0f0f0;
}

.spectate-tab-item {
  padding: 12px 24px;
  font-size: 16px;
  color: #666;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  margin-bottom: -2px;
  transition: all 0.3s;
}

.spectate-tab-item:hover {
  color: #4facfe;
}

.spectate-tab-item.active {
  color: #4facfe;
  border-bottom-color: #4facfe;
  font-weight: 500;
}

.spectate-games {
  min-height: 400px;
}

.game-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  margin-bottom: 15px;
}

.game-players {
  display: flex;
  align-items: center;
  gap: 10px;
}

.player-name.black {
  color: #333;
}

.player-name.white {
  color: #999;
}

.vs {
  font-size: 12px;
  color: #666;
  padding: 0 10px;
}

.game-info {
  display: flex;
  gap: 15px;
  font-size: 14px;
  color: #999;
}

/* ========== 对局记录 ========== */
.records-filter {
  margin: 30px 0;
}

.record-items {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.record-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
}

.record-result {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.record-result.win {
  background: #f0f9ff;
  color: #67c23a;
}

.record-result.loss {
  background: #fef0f0;
  color: #f56c6c;
}

.record-result.draw {
  background: #fef9e7;
  color: #e6a23c;
}

.record-info {
  flex: 1;
}

.record-opponent {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.record-meta {
  font-size: 12px;
  color: #999;
}

.record-meta span {
  margin-right: 15px;
}

.record-rating {
  font-size: 18px;
  font-weight: bold;
}

.record-rating.positive {
  color: #67c23a;
}

.record-rating.negative {
  color: #f56c6c;
}

/* ========== 帮助中心 ========== */
.help-categories {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 15px;
  margin: 30px 0;
}

.help-category-card {
  padding: 20px;
  background: #fafafa;
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s;
}

.help-category-card:hover {
  background: #fff5f0;
  transform: translateY(-2px);
}

.category-icon {
  font-size: 32px;
  margin-bottom: 10px;
}

.category-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
}

.category-desc {
  font-size: 12px;
  color: #999;
}

.help-articles {
  margin-top: 30px;
}

.help-articles h3 {
  font-size: 20px;
  color: #333;
  margin-bottom: 15px;
}

.help-article-card {
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  margin-bottom: 10px;
  cursor: pointer;
}

.help-article-card:hover {
  background: #fff5f0;
}

.article-title {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
}

.article-preview {
  font-size: 14px;
  color: #666;
}

/* ========== 玩家对战预览 ========== */
.players-preview {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
}

.player-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  animation: playerFloat 2s ease-in-out infinite;
}

.player-preview.player-black {
  animation-delay: 0s;
}

.player-preview.player-white {
  animation-delay: 1s;
}

@keyframes playerFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.player-avatar-preview {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
  margin-bottom: 8px;
}

.player-name-preview {
  color: white;
  font-size: 14px;
  font-weight: 600;
}

.vs-badge {
  width: 50px;
  height: 50px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: bold;
}

/* ========== 响应式 ========== */
@media (max-width: 1024px) {
  .sidebar {
    display: none;
  }

  .horizontal-nav {
    display: block;
  }

  .content-area {
    padding: 20px;
  }
}

@media (max-width: 768px) {
  .header {
    flex-direction: column;
    gap: 15px;
    padding: 15px;
  }

  .header-left {
    justify-content: center;
  }

  .logo-large {
    width: 60px;
    height: 60px;
  }

  .logo-large svg {
    width: 35px;
    height: 35px;
  }

  .header-title h1 {
    font-size: 20px;
  }

  .header-title p {
    font-size: 12px;
  }

  .header-right {
    width: 100%;
    justify-content: space-between;
  }

  .user-info-card {
    right: 15px;
  }

  .hero-banner {
    padding: 30px 20px;
  }

  .hero-content {
    flex-direction: column;
    text-align: center;
  }

  .hero-text h2 {
    font-size: 24px;
  }

  .hero-animation {
    display: none;
  }

  .pve-cards,
  .match-modes {
    grid-template-columns: 1fr;
  }

  .room-actions {
    flex-direction: column;
  }

  /* PVE 移动端优化 */
  .pve-board {
    width: 300px;
    height: 300px;
  }

  .pve-piece {
    width: 16px;
    height: 16px;
  }

  .pve-game-header {
    flex-direction: column;
    gap: 10px;
  }

  .pve-controls {
    flex-wrap: wrap;
  }

  .pve-btn {
    flex: 1;
    min-width: 80px;
    font-size: 12px;
    padding: 8px 12px;
  }
}

/* ========== 页尾 Footer ========== */
.page-footer {
  width: 100%;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border-top: 4px solid #ff6b35;
  position: relative;
  overflow: hidden;
  margin-top: auto;
}

.footer-particles {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 1;
}

.footer-container {
  position: relative;
  z-index: 2;
  max-width: 1200px;
  margin: 0 auto;
  padding: 40px 20px 20px;
}

.footer-main {
  display: grid;
  grid-template-columns: 2fr 3fr 2fr;
  gap: 40px;
  margin-bottom: 30px;
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 15px;
}

.footer-logo {
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  border-radius: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30px;
}

.footer-brand-text h3 {
  font-size: 18px;
  color: #333;
  margin-bottom: 5px;
}

.footer-brand-text p {
  font-size: 14px;
  color: #999;
}

.footer-links {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.footer-link-section h4 {
  font-size: 14px;
  color: #ff6b35;
  margin-bottom: 15px;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.footer-link-section a {
  display: block;
  color: #666;
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 10px;
  transition: color 0.3s;
}

.footer-link-section a:hover {
  color: #ff6b35;
}

.footer-stats {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.footer-stat-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}

.footer-stat-icon {
  font-size: 24px;
}

.footer-stat-content {
  flex: 1;
}

.footer-stat-value {
  font-size: 20px;
  font-weight: bold;
  color: #ff6b35;
}

.footer-stat-label {
  font-size: 12px;
  color: #999;
}

.footer-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 107, 53, 0.2);
}

.footer-copyright {
  font-size: 14px;
  color: #999;
}

.footer-social {
  display: flex;
  gap: 15px;
}

.footer-social-link {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: rgba(255, 107, 53, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ff6b35;
  transition: all 0.3s;
}

.footer-social-link:hover {
  background: #ff6b35;
  color: white;
  transform: translateY(-2px);
}

@media (max-width: 768px) {
  .footer-main {
    grid-template-columns: 1fr;
    gap: 30px;
  }

  .footer-links {
    grid-template-columns: 1fr;
  }

  .footer-bottom {
    flex-direction: column;
    gap: 15px;
    text-align: center;
  }
}
</style>
