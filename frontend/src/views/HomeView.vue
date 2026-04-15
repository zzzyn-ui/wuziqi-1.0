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
          <div class="header-user-level">{{ getRankTitle(userStore.userInfo?.rating || 700) }}</div>
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

          <div class="room-list-section">
            <div class="room-list-header">
              <h3>📋 公开房间</h3>
              <el-button @click="loadPublicRooms" :loading="loadingPublicRooms" circle size="small">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>

            <div v-if="loadingPublicRooms && publicRooms.length === 0" class="empty-state">
              <el-icon class="is-loading" :size="30"><Loading /></el-icon>
              <p>加载中...</p>
            </div>

            <div v-else-if="publicRooms.length === 0" class="empty-state">
              <el-empty description="暂无公开房间">
                <el-button type="primary" @click="createRoom">创建房间</el-button>
              </el-empty>
            </div>

            <div v-else class="room-list-items">
              <div v-for="room in publicRooms" :key="room.id" class="room-item" @click="joinPublicRoom(room.id)">
                <div class="room-icon">🏠</div>
                <div class="room-info">
                  <div class="room-name">{{ room.name }}</div>
                  <div class="room-id">房间号: {{ room.id }}</div>
                  <div class="room-meta">
                    <span class="mode-tag">{{ room.mode }}</span>
                    <span class="player-count">{{ room.playerCount }}/2人</span>
                  </div>
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

          <div class="rank-header">
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
            <el-button
              type="primary"
              size="small"
              @click="fetchRankList"
              :icon="Refresh"
              :loading="rankLoading"
            >
              刷新
            </el-button>
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
                  </div>
                  <div class="player-level">{{ getRankTitle(player.rating) }}</div>
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

          <div class="my-rank-banner" v-if="myRank > 0">
            <span class="my-rank-label">我的排名</span>
            <span class="my-rank-value">#{{ myRank }}</span>
          </div>
          <div class="my-rank-banner no-rank" v-else>
            <span class="my-rank-label">暂未上榜</span>
          </div>
        </div>

        <!-- 对局记录面板 -->
        <div class="content-panel" :class="{ active: activeNav === 'records' }">
          <div class="hero-banner" style="background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);">
            <div class="hero-content">
              <div class="hero-text">
                <h2>📜 对局记录</h2>
                <p>查看历史对局，支持复盘回放</p>
              </div>
            </div>
          </div>

          <div class="records-container">
            <div class="records-summary">
              <div class="summary-item">
                <div class="summary-value">{{ recordStats.recentGames || 0 }}</div>
                <div class="summary-label">近3天场次</div>
              </div>
              <div class="summary-item">
                <div class="summary-value">{{ recordStats.recentWins || 0 }}</div>
                <div class="summary-label">胜利</div>
              </div>
              <div class="summary-item">
                <div class="summary-value">{{ recordStats.recentWinRate || 0 }}%</div>
                <div class="summary-label">胜率</div>
              </div>
            </div>

            <div class="records-filter">
              <el-radio-group v-model="recordsFilter">
                <el-radio-button value="all">全部</el-radio-button>
                <el-radio-button value="win">胜局</el-radio-button>
                <el-radio-button value="loss">负局</el-radio-button>
                <el-radio-button value="draw">平局</el-radio-button>
              </el-radio-group>
            </div>

            <div class="records-list" v-loading="loadingRecords">
              <div v-if="filteredRecords.length === 0" class="empty-records">
                <p>暂无对局记录</p>
                <el-button type="primary" @click="activeNav = 'quick-match'">开始对局</el-button>
              </div>
              <div
                v-for="record in filteredRecords"
                :key="record.id"
                class="record-item"
                :class="{ 'win': record.isWin, 'loss': !record.isWin && record.winnerId !== null }"
                @click="viewRecordDetail(record.id)"
              >
                <div class="record-header">
                  <div class="record-result">
                    <span v-if="record.winnerId === null" class="result-draw">和棋</span>
                    <span v-else-if="record.isWin" class="result-win">胜利</span>
                    <span v-else class="result-loss">失败</span>
                  </div>
                  <div class="record-time">{{ formatRecordTime(record.createdAt) }}</div>
                </div>

                <div class="record-players">
                  <div class="player" :class="{ 'winner': record.winColor === 1 }">
                    <span class="player-color">⚫</span>
                    <span class="player-name" v-if="record.gameMode === 'pve'">玩家</span>
                    <span class="player-name" v-else>{{ record.blackPlayer.nickname }}</span>
                    <span class="player-rating">{{ record.blackPlayer.rating }}</span>
                  </div>
                  <div class="vs">VS</div>
                  <div class="player" :class="{ 'winner': record.winColor === 2 }">
                    <span class="player-color">⚪</span>
                    <span class="player-name" v-if="record.gameMode === 'pve'">人机（{{ getAIDifficulty(record.whitePlayer.rating || 0) }}）</span>
                    <span class="player-name" v-else>{{ record.whitePlayer.nickname }}</span>
                    <span class="player-rating" v-if="record.gameMode !== 'pve'">{{ record.whitePlayer.rating }}</span>
                  </div>
                </div>

                <div class="record-info">
                  <span class="info-item">{{ record.gameMode === 'pve' ? '人机' : '对战' }}</span>
                  <span class="info-item">{{ record.moveCount }}手</span>
                  <span class="info-item">{{ formatRecordDuration(record.duration) }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 复盘对话框 -->
        <el-dialog
          v-model="showReplayDialog"
          title="对局复盘"
          width="90%"
          :close-on-click-modal="false"
          class="replay-dialog"
        >
          <div v-if="currentRecord" class="replay-content">
            <!-- 复盘信息 -->
            <div class="replay-info">
              <div class="replay-players">
                <span class="replay-player">
                  <span class="color-icon">⚫</span>
                  <span v-if="isPVEGame">
                    玩家
                  </span>
                  <span v-else>
                    {{ currentRecord.blackPlayer?.nickname || '黑方' }}
                  </span>
                  ({{ currentRecord.blackPlayer?.rating || 0 }})
                </span>
                <span class="vs">VS</span>
                <span class="replay-player">
                  <span class="color-icon">⚪</span>
                  <span v-if="isPVEGame">
                    人机（{{ getAIDifficulty(currentRecord.whitePlayer?.rating || 0) }}）
                  </span>
                  <span v-else>
                    {{ currentRecord.whitePlayer?.nickname || '白方' }}
                  </span>
                  <span v-if="!isPVEGame">({{ currentRecord.whitePlayer?.rating || 0 }})</span>
                </span>
              </div>
              <div class="replay-result">
                <span v-if="currentRecord.winnerId === null">和棋</span>
                <span v-else-if="currentRecord.winColor === 1">黑方胜</span>
                <span v-else>白方胜</span>
              </div>
              <div class="replay-record-id">记录ID: {{ currentRecord.id }}</div>
            </div>

            <!-- 无走棋数据提示 -->
            <div v-if="replayMoves.length === 0" class="no-moves-warning">
              ⚠️ 该对局记录没有走棋数据（这是旧记录）
              <br>
              请完成一局新的对局后再试
            </div>

            <!-- 棋盘 -->
            <div class="replay-board">
              <GameBoard
                :board="replayBoardState"
                :last-move="replayLastMove ? { row: replayLastMove.x, col: replayLastMove.y } : null"
                :disabled="true"
              />
            </div>

            <!-- 控制面板 -->
            <div class="replay-controls">
              <el-button @click="replayFirstMove" :disabled="currentMoveIndex <= 0">
                ⏮ 开始
              </el-button>
              <el-button @click="replayPrevMove" :disabled="currentMoveIndex <= 0">
                ◀ 上一步
              </el-button>
              <el-button @click="toggleAutoPlay" :type="isAutoPlaying ? 'warning' : 'primary'">
                {{ isAutoPlaying ? '⏸ 暂停' : '▶ 自动播放' }}
              </el-button>
              <el-button @click="replayNextMove" :disabled="currentMoveIndex >= replayMoves.length">
                下一步 ▶
              </el-button>
              <el-button @click="replayToLastMove" :disabled="currentMoveIndex >= replayMoves.length">
                结束 ⏭
              </el-button>
              <div class="move-indicator">
                第 {{ currentMoveIndex }} / {{ replayMoves.length }} 手
              </div>
            </div>
          </div>
        </el-dialog>

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
                  <div class="friend-actions">
                    <div class="friend-status" :class="{ online: friend.online }">
                      {{ friend.online ? '在线' : '离线' }}
                    </div>
                    <el-button
                      type="primary"
                      size="small"
                      @click="handleChatWithFriend(friend)"
                    >
                      聊天
                    </el-button>
                    <el-button
                      type="success"
                      size="small"
                      @click="handleInviteFriend(friend)"
                    >
                      邀请
                    </el-button>
                    <el-button
                      type="danger"
                      size="small"
                      @click="deleteFriend(friend)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="activeFriendsTab === 'requests'" class="requests-section">
              <div class="section-header">
                <div style="display: flex; align-items: center; gap: 10px;">
                  <h3>好友申请</h3>
                  <el-badge v-if="pendingRequests.length > 0" :value="pendingRequests.length" />
                </div>
                <el-button
                  type="primary"
                  size="small"
                  @click="fetchPendingRequests"
                  :icon="Refresh"
                >
                  刷新
                </el-button>
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

            <div v-if="activeFriendsTab === 'recommendations'" class="recommendations-section">
              <div class="section-header">
                <div style="display: flex; align-items: center; gap: 10px;">
                  <h3>推荐好友</h3>
                  <span v-if="recommendedUsers.length > 0" style="font-size: 12px; color: #999;">
                    为你推荐 {{ recommendedUsers.length }} 位用户
                  </span>
                </div>
                <el-button
                  type="primary"
                  size="small"
                  @click="fetchRecommendations"
                  :icon="Refresh"
                >
                  刷新推荐
                </el-button>
              </div>

              <div v-if="recommendedUsers.length === 0" class="empty-state">
                <p>暂无推荐用户</p>
                <p style="font-size: 12px; color: #999;">可能所有用户都已经是你的好友了</p>
              </div>

              <div v-else class="recommendation-items">
                <div v-for="user in recommendedUsers" :key="user.id" class="recommendation-card">
                  <div class="recommendation-avatar">{{ user.nickname?.charAt(0) || user.username?.charAt(0) || '?' }}</div>
                  <div class="recommendation-info">
                    <div class="recommendation-name">{{ user.nickname || user.username }}</div>
                    <div class="recommendation-details">
                      <span class="level-badge">Lv.{{ user.level }}</span>
                      <span class="rating-badge">{{ user.rating }}分</span>
                      <span v-if="user.online" class="online-badge">在线</span>
                    </div>
                  </div>
                  <el-button
                    type="primary"
                    size="small"
                    @click="sendFriendRequest(user)"
                  >
                    添加
                  </el-button>
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
              <a href="#" @click.prevent="handleNavClick(gameNavItems[3])">观战模式</a>
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

    <!-- 创建房间对话框 -->
    <el-dialog
      v-model="showCreateRoomDialog"
      title="创建房间"
      width="450px"
      :close-on-click-modal="false"
    >
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="房间名称">
          <el-input v-model="createForm.roomName" placeholder="输入房间名称" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="房间密码">
          <el-input v-model="createForm.password" type="password" placeholder="留空为公开房间" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancelCreateRoom">取消</el-button>
        <el-button type="primary" @click="handleConfirmCreateRoom" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 加入房间对话框 -->
    <el-dialog
      v-model="showJoinRoomDialog"
      title="加入房间"
      width="450px"
      :close-on-click-modal="false"
    >
      <el-form :model="joinForm" label-width="100px">
        <el-form-item label="房间ID">
          <el-input v-model="joinForm.roomId" placeholder="输入房间ID" />
        </el-form-item>
        <el-form-item label="房间密码">
          <el-input v-model="joinForm.password" type="password" placeholder="如有密码请输入" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleCancelJoinRoom">取消</el-button>
        <el-button type="primary" @click="handleConfirmJoinRoom" :loading="joining">加入</el-button>
      </template>
    </el-dialog>

    <!-- 添加好友对话框 -->
    <el-dialog
      v-model="showAddFriendDialog"
      title="添加好友"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="addFriendForm" label-width="100px">
        <el-form-item label="对方用户名">
          <el-input v-model="addFriendForm.username" placeholder="请输入要添加的用户名" />
        </el-form-item>
        <el-form-item label="验证消息">
          <el-input
            v-model="addFriendForm.message"
            type="textarea"
            :rows="3"
            placeholder="请输入验证消息（可选）"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddFriendDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSendFriendRequest" :loading="sendingFriendRequest">发送申请</el-button>
      </template>
    </el-dialog>

    <!-- 聊天对话框 -->
    <el-dialog
      v-model="showChatDialog"
      title=""
      width="500px"
      @close="closeChatDialog"
      class="chat-dialog"
    >
      <template #header>
        <div class="chat-dialog-header">
          <h3>{{ chatFriend?.nickname || chatFriend?.username }}</h3>
          <span v-if="chatFriend?.online" class="chat-status online">在线</span>
          <span v-else class="chat-status offline">离线</span>
        </div>
      </template>

      <div class="chat-panel">
        <div class="chat-messages" ref="chatMessagesRef">
          <div v-if="chatMessages.length === 0" class="chat-empty">
            暂无消息，开始聊天吧~
          </div>
          <div
            v-for="(msg, index) in chatMessages"
            :key="index"
            class="chat-message"
            :class="{ 'my-message': msg.isMine }"
          >
            <div class="message-sender">{{ msg.sender }}</div>
            <div class="message-content">{{ msg.content }}</div>
            <div class="message-time">{{ msg.time }}</div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="chatInput"
            placeholder="输入消息..."
            @keyup.enter="sendChatMessage"
            :maxlength="100"
            :disabled="!chatFriend?.online"
          />
          <el-button type="primary" @click="sendChatMessage" :disabled="!chatInput.trim() || !chatFriend?.online">发送</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Loading } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useGameStore } from '@/store/modules/game'
import { wsClient } from '@/api/websocket'
import { get } from '@/api/http'
import { userApi, friendApi } from '@/api'
import GameBoard from '@/components/shared/GameBoard.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const gameStore = useGameStore()

const horizontalNavScroll = ref<HTMLElement | null>(null)

// 监听路由变化，根据URL参数切换面板
watch(() => route.query.panel, (newPanel) => {
  console.log('[Home] Panel changed:', newPanel)
  if (newPanel && typeof newPanel === 'string') {
    const validPanels = ['pve', 'quick-match', 'room', 'spectate', 'friends', 'rank', 'records', 'help']
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

// 公开房间数据
const publicRooms = ref<any[]>([])
const loadingPublicRooms = ref(false)

const stats = ref({
  totalGames: 0,
  wins: 0,
  losses: 0,
  winRate: 0
})

// 排行榜数据
const rankLoading = ref(false)
const activeRankTab = ref('all')
const rankList = ref<any[]>([])
const myRank = ref<number | null>(null)

// 对局记录数据
const records = ref<any[]>([])
const recordStats = ref<any>({})
const loadingRecords = ref(false)
const recordsFilter = ref('all')

// 复盘相关
const showReplayDialog = ref(false)
const currentRecord = ref<any>(null)
const replayMoves = ref<any[]>([])
const replayBoardState = ref<number[][]>([])
const replayLastMove = ref<{ x: number; y: number } | null>(null)
const currentMoveIndex = ref(0)
const isAutoPlaying = ref(false)
let autoPlayTimer: number | null = null

// 筛选后的对局记录
const filteredRecords = computed(() => {
  if (recordsFilter.value === 'all') return records.value
  if (recordsFilter.value === 'win') return records.value.filter(r => r.isWin)
  if (recordsFilter.value === 'loss') return records.value.filter(r => !r.isWin && r.winnerId !== null)
  if (recordsFilter.value === 'draw') return records.value.filter(r => r.winnerId === null)
  return records.value
})

// 判断是否是PVE游戏
const isPVEGame = computed(() => {
  return currentRecord.value && (
    currentRecord.value.gameMode === 'pve' ||
    (currentRecord.value.whitePlayer?.id === currentRecord.value.blackPlayer?.id)
  )
})

// 好友系统数据
const activeFriendsTab = ref('list')
const friendSearchText = ref('')
const showAddFriendDialog = ref(false)
const friendsList = ref<any[]>([])
const pendingRequests = ref<any[]>([])
const recommendedUsers = ref<any[]>([])
const sendingFriendRequest = ref(false)

// 好友申请定时刷新
let friendRequestRefreshInterval: ReturnType<typeof setInterval> | null = null

// 添加好友表单
const addFriendForm = reactive({
  username: '',
  message: ''
})

const friendsTabs = [
  { label: '好友列表', value: 'list' },
  { label: '好友申请', value: 'requests' },
  { label: '推荐好友', value: 'recommendations' }
]

const filteredFriends = computed(() => {
  if (!friendSearchText.value) return friendsList.value
  const search = friendSearchText.value.toLowerCase()
  return friendsList.value.filter(f =>
    f.username.toLowerCase().includes(search) ||
    (f.nickname && f.nickname.toLowerCase().includes(search))
  )
})

// 残局挑战数据（已删除）


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

    if (response.code === 200) {
      let list = response.data || []

      // 如果数据不足10条，使用模拟数据补充
      if (list.length < 10) {
        const mockPlayers = generateMockPlayers(10 - list.length)
        list = [...list, ...mockPlayers]
        // 合并后按积分降序排序
        list.sort((a: any, b: any) => (b.rating || 0) - (a.rating || 0))
      }

      // 保持顺序，映射数据
      rankList.value = list.map((player: any, index: number) => ({
        ...player,
        isMe: player.id === userStore.userInfo?.id,
        rankNumber: index + 1
      }))

      // 计算用户自己的排名（在排序后的数据中计算）
      const myIndex = list.findIndex((p: any) => p.id === userStore.userInfo?.id)
      myRank.value = myIndex >= 0 ? myIndex + 1 : 0
    } else {
      // 如果请求失败，使用模拟数据
      const mockData = generateMockPlayers(10)
      mockData.sort((a: any, b: any) => b.rating - a.rating)
      rankList.value = mockData.map((player: any, index: number) => ({
        ...player,
        isMe: false,
        rankNumber: index + 1
      }))
      myRank.value = 0
    }
  } catch (error) {
    console.error('[Home] 获取排行榜失败:', error)
    // 失败时使用模拟数据
    const mockData = generateMockPlayers(10)
    mockData.sort((a: any, b: any) => b.rating - a.rating)
    rankList.value = mockData.map((player: any, index: number) => ({
      ...player,
      isMe: false,
      rankNumber: index + 1
    }))
    myRank.value = 0
  } finally {
    rankLoading.value = false
  }
}

// 对局记录相关方法
const fetchRecords = async () => {
  if (!userStore.userInfo?.id) return

  loadingRecords.value = true
  try {
    const response = await get('/record/list', {
      params: {
        userId: userStore.userInfo.id,
        days: 3
      }
    })

    if (response.code === 200) {
      records.value = response.data || []
    }
  } catch (error) {
    console.error('[Home] 获取对局记录失败:', error)
  } finally {
    loadingRecords.value = false
  }
}

const fetchRecordStats = async () => {
  if (!userStore.userInfo?.id) return

  try {
    const response = await get('/record/stats', {
      params: {
        userId: userStore.userInfo.id
      }
    })

    if (response.code === 200) {
      recordStats.value = response.data || {}
    }
  } catch (error) {
    console.error('[Home] 获取对局统计失败:', error)
  }
}

const viewRecordDetail = async (recordId: number) => {
  try {
    console.log('[复盘] 请求对局详情, recordId:', recordId)
    const response = await get(`/record/${recordId}`)

    if (response.code === 200) {
      currentRecord.value = response.data
      console.log('[复盘] 原始数据:', currentRecord.value)
      console.log('[复盘] moves字段:', currentRecord.value.moves)
      console.log('[复盘] moveCount字段:', currentRecord.value.moveCount)

      // 解析走棋记录
      const movesStr = currentRecord.value.moves || '[]'
      console.log('[复盘] moves字符串:', movesStr)

      try {
        replayMoves.value = JSON.parse(movesStr)
        console.log('[复盘] 解析后的走棋记录:', replayMoves.value)
      } catch (e) {
        console.error('[复盘] 解析走棋记录失败:', e)
        replayMoves.value = []
      }

      // 解析棋盘状态（从逗号分隔的字符串转换为二维数组）
      const boardStateStr = currentRecord.value.boardState
      if (boardStateStr) {
        try {
          const values = boardStateStr.split(',').map(v => parseInt(v.trim()))
          replayBoardState.value = []
          for (let i = 0; i < 15; i++) {
            replayBoardState.value.push(values.slice(i * 15, (i + 1) * 15))
          }
        } catch (e) {
          console.error('解析棋盘状态失败:', e)
          replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))
        }
      } else {
        replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))
      }

      console.log('[复盘] 最终走棋记录:', replayMoves.value)
      console.log('[复盘] 走棋数量:', replayMoves.value.length)

      if (replayMoves.value.length === 0) {
        ElMessage.warning('该对局记录没有走棋数据，请完成一局新的对局后再试')
      }

      currentMoveIndex.value = replayMoves.value.length
      replayLastMove.value = replayMoves.value.length > 0 ? replayMoves.value[replayMoves.value.length - 1] : null

      showReplayDialog.value = true
    } else {
      ElMessage.error(response.message || '获取失败')
    }
  } catch (error: any) {
    console.error('获取对局详情失败:', error)
    ElMessage.error('获取对局详情失败')
  }
}

// 复盘控制函数
const replayFirstMove = () => {
  currentMoveIndex.value = 0
  replayLastMove.value = null
  replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))
}

const replayPrevMove = () => {
  if (currentMoveIndex.value > 0) {
    currentMoveIndex.value--
    applyMovesToBoard()
  }
}

const replayNextMove = () => {
  if (currentMoveIndex.value < replayMoves.value.length) {
    currentMoveIndex.value++
    applyMovesToBoard()
  }
}

const replayToLastMove = () => {
  currentMoveIndex.value = replayMoves.value.length
  applyMovesToBoard()
}

const toggleAutoPlay = () => {
  if (isAutoPlaying.value) {
    stopAutoPlay()
  } else {
    startAutoPlay()
  }
}

const startAutoPlay = () => {
  isAutoPlaying.value = true
  autoPlayTimer = window.setInterval(() => {
    if (currentMoveIndex.value < replayMoves.value.length) {
      replayNextMove()
    } else {
      stopAutoPlay()
    }
  }, 1000)
}

const stopAutoPlay = () => {
  isAutoPlaying.value = false
  if (autoPlayTimer) {
    clearInterval(autoPlayTimer)
    autoPlayTimer = null
  }
}

const applyMovesToBoard = () => {
  replayBoardState.value = Array(15).fill(null).map(() => Array(15).fill(0))

  for (let i = 0; i < currentMoveIndex.value && i < replayMoves.value.length; i++) {
    const move = replayMoves.value[i]
    const color = i % 2 === 0 ? 1 : 2
    replayBoardState.value[move.x][move.y] = color
  }

  if (currentMoveIndex.value > 0 && currentMoveIndex.value <= replayMoves.value.length) {
    replayLastMove.value = replayMoves.value[currentMoveIndex.value - 1]
  } else {
    replayLastMove.value = null
  }
}

const formatRecordTime = (timeStr: string) => {
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  if (diff < 3600000) {
    return `${Math.floor(diff / 60000)}分钟前`
  } else if (diff < 86400000) {
    return `${Math.floor(diff / 3600000)}小时前`
  } else {
    return `${Math.floor(diff / 86400000)}天前`
  }
}

const formatRecordDuration = (seconds: number) => {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

// 获取AI难度名称
const getAIDifficulty = (rating: number): string => {
  if (rating >= 2000) return '专家'
  if (rating >= 1600) return '困难'
  if (rating >= 1200) return '中等'
  return '简单'
}

// 生成模拟排行榜数据
const generateMockPlayers = (count: number) => {
  // 不同积分范围的玩家
  const players = [
    // 高分段
    { username: 'legend_master', nickname: '传奇大师', rating: 2500, totalGames: 600, winRate: 78 },
    { username: 'grand_champion', nickname: '冠军王者', rating: 2300, totalGames: 550, winRate: 75 },
    { username: 'elite_player', nickname: '精英选手', rating: 2100, totalGames: 480, winRate: 72 },
    // 中高分段
    { username: 'master_level', nickname: '大师级', rating: 1900, totalGames: 420, winRate: 68 },
    { username: 'expert_player', nickname: '专家玩家', rating: 1750, totalGames: 380, winRate: 65 },
    { username: 'skilled_fighter', nickname: '熟练战士', rating: 1600, totalGames: 320, winRate: 62 },
    // 中分段
    { username: 'intermediate', nickname: '中级选手', rating: 1400, totalGames: 250, winRate: 58 },
    { username: 'average_player', nickname: '普通玩家', rating: 1250, totalGames: 200, winRate: 52 },
    // 中低分段
    { username: 'casual_player', nickname: '休闲玩家', rating: 1100, totalGames: 150, winRate: 48 },
    { username: 'beginner_friendly', nickname: '友好新手', rating: 950, totalGames: 100, winRate: 45 },
    // 低分段
    { username: 'learning_player', nickname: '学习中的', rating: 850, totalGames: 80, winRate: 42 },
    { username: 'rookie_player', nickname: '菜鸟玩家', rating: 750, totalGames: 60, winRate: 38 },
    { username: 'novice_fighter', nickname: '新手战士', rating: 650, totalGames: 40, winRate: 35 },
    { username: 'starter_player', nickname: '起步玩家', rating: 550, totalGames: 30, winRate: 30 },
    { username: 'fresh_beginner', nickname: '纯新手', rating: 450, totalGames: 20, winRate: 25 }
  ]

  // 随机打乱并取指定数量
  const shuffled = [...players].sort(() => Math.random() - 0.5)

  return shuffled.slice(0, count).map((player, i) => ({
    id: 1000 + i,
    username: player.username,
    nickname: player.nickname,
    avatar: '',
    rating: player.rating,
    totalGames: player.totalGames,
    winRate: player.winRate,
    level: Math.floor(player.rating / 200),
    isMe: false
  }))
}

// 好友系统函数
const fetchFriends = async () => {
  try {
    const response = await friendApi.getFriendList(userStore.userInfo!.id)
    if (response.code === 200) {
      friendsList.value = response.data?.list || []
      console.log('[Home] 好友列表加载成功:', friendsList.value)
      friendsList.value.forEach((friend, index) => {
        console.log(`[Home] 好友 ${index}:`, friend)
      })
    }
  } catch (error) {
    console.error('[Home] 获取好友列表失败:', error)
  }
}

const fetchPendingRequests = async () => {
  try {
    const response = await friendApi.getFriendRequests(userStore.userInfo!.id)
    if (response.code === 200) {
      pendingRequests.value = response.data?.list || []
    }
  } catch (error) {
    console.error('[Home] 获取好友申请失败:', error)
  }
}

const acceptRequest = async (id: number) => {
  try {
    await friendApi.handleFriendRequest(id, userStore.userInfo!.id, true)
    pendingRequests.value = pendingRequests.value.filter(r => r.id !== id)
    // 刷新好友列表
    fetchFriends()
  } catch (error) {
    console.error('[Home] 接受好友申请失败:', error)
    ElMessage.error('操作失败')
  }
}

const rejectRequest = async (id: number) => {
  try {
    await friendApi.handleFriendRequest(id, userStore.userInfo!.id, false)
    pendingRequests.value = pendingRequests.value.filter(r => r.id !== id)
  } catch (error) {
    console.error('[Home] 拒绝好友申请失败:', error)
    ElMessage.error('操作失败')
  }
}

// 获取推荐好友
const fetchRecommendations = async () => {
  try {
    // 先刷新好友列表，确保数据最新
    await fetchFriends()

    // 获取排行榜上的用户作为推荐
    const response = await get('/rank/all')
    if (response.code === 200) {
      const allUsers = response.data || []

      // 过滤掉自己和已经是好友的用户
      const friendIds = friendsList.value.map(f => f.userId)
      let filteredUsers = allUsers.filter((user: any) =>
        user.id !== userStore.userInfo?.id &&
        !friendIds.includes(user.id)
      )

      // 随机打乱数组
      const shuffled = filteredUsers.sort(() => Math.random() - 0.5)

      // 只取前3个作为推荐
      const recommendations = shuffled.slice(0, 3)

      recommendedUsers.value = recommendations

      console.log('[Home] 推荐好友数量:', recommendations.length)
    }
  } catch (error) {
    console.error('[Home] 获取推荐好友失败:', error)
    // 失败时显示空数组
    recommendedUsers.value = []
  }
}

const sendFriendRequest = (user: any) => {
  addFriendForm.username = user.username
  showAddFriendDialog.value = true
}

const deleteFriend = async (friend: any) => {
  try {
    await ElMessageBox.confirm(
      `确定删除好友 ${friend.nickname || friend.username}？`,
      '确认删除',
      { type: 'warning' }
    )
    await friendApi.deleteFriend(userStore.userInfo!.id, friend.userId)
    // 刷新好友列表
    fetchFriends()
  } catch (error: any) {
    if (error !== 'cancel') {
      console.error('[Home] 删除好友失败:', error)
      ElMessage.error('删除好友失败')
    }
  }
}

// 聊天相关状态
const showChatDialog = ref(false)
const chatFriend = ref<any>(null)
const chatMessages = ref<Array<{ sender: string; content: string; time: string; isMine: boolean }>>([])
const chatInput = ref('')
const chatMessagesRef = ref<HTMLElement | null>(null)

const handleChatWithFriend = (friend: any) => {
  console.log('[Home] 点击聊天按钮，好友信息:', friend)
  console.log('[Home] 好友在线状态:', friend.online)

  chatFriend.value = friend
  chatMessages.value = []
  showChatDialog.value = true

  console.log('[Home] 打开聊天对话框，好友:', chatFriend.value)
  console.log('[Home] 聊天历史订阅ID:', globalChatHistorySubId)
  console.log('[Home] WebSocket 连接状态:', wsClient.isConnected())

  // 检查 WebSocket 连接状态
  if (!wsClient.isConnected()) {
    console.error('[Home] ❌ WebSocket 未连接，尝试重新连接...')
    const token = localStorage.getItem('token')
    if (token) {
      wsClient.connect(token).then(() => {
        console.log('[Home] WebSocket 重新连接成功')
        loadChatHistoryForFriend(friend)
      }).catch((error) => {
        console.error('[Home] WebSocket 重新连接失败:', error)
        ElMessage.error('连接失败，请刷新页面')
      })
      return
    }
  }

  // 直接加载聊天历史
  loadChatHistoryForFriend(friend)

  // 标记消息为已读
  wsClient.markMessagesAsRead(friend.userId)
}

// 加载好友聊天历史
const loadChatHistoryForFriend = (friend: any) => {
  const friendId = friend.userId

  console.log('[Home] ========== 加载聊天历史 ==========')
  console.log('[Home] 好友ID:', friendId)
  console.log('[Home] 当前订阅ID:', globalChatHistorySubId)

  // 首先尝试从缓存加载
  const loadedFromCache = loadChatHistoryFromCache(friendId)
  if (loadedFromCache) {
    console.log('[Home] ✅ 已从缓存加载聊天历史')
  } else {
    console.log('[Home] 📜 缓存中没有历史，需要从服务器获取')
  }

  // 🔧 不重新创建订阅，直接使用现有订阅
  console.log('[Home] 📜 使用现有订阅请求聊天历史')

  // 立即请求历史消息
  console.log('[Home] 📜 发送聊天历史请求: friendId=', friendId)
  wsClient.getChatHistory(friendId, 50)
  console.log('[Home] 📜 请求已发送')
}

const closeChatDialog = () => {
  showChatDialog.value = false
  chatFriend.value = null
  chatMessages.value = []
  chatInput.value = ''
}

const sendChatMessage = () => {
  if (!chatFriend.value || !chatInput.value.trim()) {
    return
  }

  if (!chatFriend.value.online) {
    ElMessage.warning('好友离线，无法发送消息')
    return
  }

  const content = chatInput.value.trim()
  console.log('[Home] 发送聊天消息:', content, '给好友:', chatFriend.value.userId)

  // 立即显示消息（乐观更新），使用临时 ID
  const tempId = Date.now()
  const myName = userStore.userInfo?.nickname || userStore.userInfo?.username || '我'
  const time = formatChatTime(new Date())
  addChatMessage(myName, content, true, time, tempId)

  // 发送到服务器
  wsClient.sendPrivateMessage(chatFriend.value.userId, content)
  chatInput.value = ''
}

const addChatMessage = (sender: string, content: string, isMine: boolean, time: string, tempId?: number) => {
  chatMessages.value.push({
    sender,
    content,
    time,
    isMine,
    tempId // 临时ID，用于替换
  })

  nextTick(() => {
    scrollToBottom()
  })
}

const scrollToBottom = () => {
  if (chatMessagesRef.value) {
    chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
  }
}

const formatChatTime = (date: Date) => {
  const hours = date.getHours().toString().padStart(2, '0')
  const minutes = date.getMinutes().toString().padStart(2, '0')
  return `${hours}:${minutes}`
}

const formatMessageTime = (time: string | Date) => {
  const date = new Date(time)
  return formatChatTime(date)
}

// 从缓存加载聊天历史
const loadChatHistoryFromCache = (friendId: number) => {
  const cached = chatHistoryCache.value.get(friendId)
  if (cached && cached.length > 0) {
    console.log('[Home] 📦 从缓存加载聊天历史: friendId=', friendId, ', 消息数:', cached.length)

    // 🔧 创建数组的副本并反转，避免修改原始缓存
    const messagesToDisplay = [...cached].reverse().map((msg: any) => ({
      sender: msg.senderNickname || msg.senderUsername || '好友',
      content: msg.content,
      time: formatMessageTime(msg.createdAt),
      isMine: msg.senderId === userStore.userInfo?.id
    }))

    chatMessages.value = messagesToDisplay

    nextTick(() => {
      scrollToBottom()
    })
    return true
  }
  console.log('[Home] 📦 缓存中没有聊天历史: friendId=', friendId)
  return false
}

const handleInviteFriend = (friend: any) => {
  console.log('[Home] 点击邀请按钮，好友信息:', friend)

  if (friend.online === false) {
    ElMessage.warning('好友离线，无法邀请')
    return
  }
  if (friend.inGame === true) {
    ElMessage.warning('好友正在游戏中，无法邀请')
    return
  }

  console.log('[Home] 发送游戏邀请给好友:', friend.userId)
  // 发送游戏邀请
  gameStore.sendGameInvitation(friend.userId, 'casual')
  ElMessage.success('已发送游戏邀请')
}

const handleSendFriendRequest = async () => {
  if (!addFriendForm.username.trim()) {
    ElMessage.warning('请输入对方用户名')
    return
  }

  sendingFriendRequest.value = true
  try {
    // 先搜索用户获取ID
    const searchResponse = await userApi.searchUsers(addFriendForm.username)
    if (!searchResponse.data?.list || searchResponse.data.list.length === 0) {
      ElMessage.error('未找到该用户')
      return
    }

    const targetUser = searchResponse.data.list[0]
    await friendApi.sendFriendRequest({
      userId: userStore.userInfo!.id,
      targetUserId: targetUser.id,
      message: addFriendForm.message
    })

    showAddFriendDialog.value = false
    addFriendForm.username = ''
    addFriendForm.message = ''
  } catch (error: any) {
    console.error('[Home] 发送好友申请失败:', error)
    ElMessage.error('发送申请失败')
  } finally {
    sendingFriendRequest.value = false
  }
}

// 观战模式函数
const watchGame = (roomId: string) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 跳转到观战页面
  router.push(`/observer/${roomId}`)
}

const watchReplay = (gameId: string) => {
  // 回放功能开发中，暂时不显示提示
}

// 加载可观战房间列表
const loadObservableRooms = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    return
  }

  try {
    console.log('[Home] 开始加载可观战房间列表')

    // 方案1：使用HTTP API直接获取
    const response = await get('/observer/rooms')
    if (response.code === 200 && response.data?.list) {
      const rooms = response.data.list
      console.log('[Home] HTTP API获取到房间列表:', rooms.length, '个房间')
      liveGames.value = rooms.map((room: any) => ({
        id: room.roomId,
        blackPlayer: room.player1?.username || room.player1?.nickname || '黑方',
        whitePlayer: room.player2?.username || room.player2?.nickname || '白方',
        mode: room.gameMode === 'casual' ? '休闲' : '竞技',
        moveCount: 0
      }))
      console.log('[Home] liveGames已更新:', liveGames.value.length)
    }

    // 方案2：同时也通过WebSocket请求（用于实时更新）
    if (wsClient.isConnected()) {
      wsClient.send('/app/observer/rooms', {})
    }
  } catch (error) {
    console.error('[Home] 加载可观战房间失败:', error)
    ElMessage.error('加载房间列表失败')
  }
}

// 订阅观战相关消息
let observableRoomsSubscription = ''

// 在onMounted中订阅观战房间列表
const originalOnMounted = onMounted
// ...需要找到合适的位置添加订阅逻辑

// 对局记录函数
const viewReplay = (recordId: string) => {
  // 复盘功能开发中，暂时不显示提示
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

// 根据积分获取专业称号（与个人资料页面一致）
const getRankTitle = (rating: number): string => {
  if (rating < 1000) return 'Lv.1 入门棋手'
  if (rating < 1200) return 'Lv.2 初级棋手'
  if (rating < 1400) return 'Lv.3 初级棋手'
  if (rating < 1600) return 'Lv.4 中级棋手'
  if (rating < 1800) return 'Lv.5 中级棋手'
  if (rating < 2000) return 'Lv.6 高级棋手'
  if (rating < 2200) return 'Lv.7 棋士'
  if (rating < 2400) return 'Lv.8 高手'
  if (rating < 2600) return 'Lv.9 大师'
  return 'Lv.10 宗师'
}

// 导航点击处理
const handleNavClick = (item: any) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 跳转到带参数的首页
  if (item.id === 'spectate') {
    // 加载观战房间列表
    router.push({ path: '/home', query: { panel: 'spectate' } })
    // 需要等待路由更新后再加载
    setTimeout(() => loadObservableRooms(), 100)
  } else if (item.id) {
    router.push({ path: '/home', query: { panel: item.id } })
  } else if (item.path) {
    router.push(item.path)
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
const showCreateRoomDialog = ref(false)
const createForm = reactive({
  roomName: '',
  password: ''
})
const creating = ref(false)

const createRoom = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 确保WebSocket已连接
  const token = localStorage.getItem('token')
  if (token && !wsClient.isConnected()) {
    try {
      await wsClient.connect(token)
    } catch (error) {
      ElMessage.error('服务器连接失败，请重试')
      return
    }
  }

  showCreateRoomDialog.value = true
}

// 确认创建房间
const handleConfirmCreateRoom = async () => {
  if (!createForm.roomName.trim()) {
    ElMessage.warning('请输入房间名称')
    return
  }

  console.log('[Home] 开始创建房间:', createForm)

  creating.value = true
  try {
    wsClient.send('/app/room/create', {
      roomName: createForm.roomName,
      password: createForm.password
    })
    console.log('[Home] 创建房间消息已发送')

    // 5秒后自动重置loading状态（避免一直转圈）
    setTimeout(() => {
      if (creating.value) {
        console.warn('[Home] 创建房间超时，重置状态')
        creating.value = false
        ElMessage.warning('创建房间超时，请重试')
      }
    }, 5000)
  } catch (error) {
    console.error('[Home] 创建房间失败:', error)
    ElMessage.error('创建房间失败')
    creating.value = false
  }
}

// 取消创建房间
const handleCancelCreateRoom = () => {
  showCreateRoomDialog.value = false
  createForm.roomName = ''
  createForm.password = ''
}

// 加入房间
const showJoinRoomDialog = ref(false)
const joinForm = reactive({
  roomId: '',
  password: ''
})
const joining = ref(false)

// 订阅房间相关消息
let roomCreatedSubId = ''
let roomJoinedSubId = ''
let roomErrorSubId = ''
let roomListSubId = ''
let observableRoomsSubId = '' // 观战房间列表订阅
let globalPrivateChatSubId = '' // 全局私聊消息订阅
let globalChatHistorySubId = '' // 全局聊天历史订阅
let chatHistorySubscriptionReady = false // 聊天历史订阅就绪标志

// 聊天历史缓存：按好友ID缓存聊天历史
const chatHistoryCache = ref<Map<number, Array<any>>>(new Map())

const joinRoom = async () => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 确保WebSocket已连接
  const token = localStorage.getItem('token')
  if (token && !wsClient.isConnected()) {
    try {
      await wsClient.connect(token)
    } catch (error) {
      ElMessage.error('服务器连接失败，请重试')
      return
    }
  }

  showJoinRoomDialog.value = true
}

// 确认加入房间
const handleConfirmJoinRoom = async () => {
  if (!joinForm.roomId.trim()) {
    ElMessage.warning('请输入房间ID')
    return
  }

  joining.value = true
  try {
    // 确保WebSocket已连接
    const token = localStorage.getItem('token')
    if (token && !wsClient.isConnected()) {
      console.log('[Home] 加入房间：WebSocket未连接，正在连接...')
      await wsClient.connect(token)
      console.log('[Home] 加入房间：WebSocket连接成功')
    }

    // 确保订阅已建立（重新订阅）
    const userId = userStore.userInfo?.id
    if (userId) {
      const userTopic = `/topic/user/${userId}/room`

      // 检查订阅是否存在
      if (!roomJoinedSubId || !roomErrorSubId) {
        console.log('[Home] 加入房间：订阅不存在，正在建立订阅...')

        // 重新订阅房间加入消息
        if (!roomJoinedSubId) {
          roomJoinedSubId = wsClient.subscribe(userTopic, (data: any) => {
            console.log('[Home] 收到房间加入消息:', data)
            if (data.type === 'ROOM_JOINED' && data.success) {
              showJoinRoomDialog.value = false
              joining.value = false
              // 跳转到游戏页面
              router.push(`/game/${data.roomId}`)
            } else if (data.type === 'ROOM_ERROR') {
              ElMessage.error(data.message || '操作失败')
              showJoinRoomDialog.value = false
              joining.value = false
            }
          })
          console.log('[Home] 房间加入订阅已建立, topic:', userTopic)
        }

        // 重新订阅房间错误消息
        if (!roomErrorSubId) {
          roomErrorSubId = wsClient.subscribe(userTopic, (data: any) => {
            console.log('[Home] 收到房间错误消息:', data)
            if (data.type === 'ROOM_ERROR') {
              ElMessage.error(data.message || '操作失败')
              creating.value = false
              joining.value = false
              loadingPublicRooms.value = false
            }
          })
          console.log('[Home] 房间错误订阅已建立, topic:', userTopic)
        }

        // 等待一小段时间确保订阅建立完成
        await new Promise(resolve => setTimeout(resolve, 200))
      }
    }

    // 发送加入房间请求
    console.log('[Home] 发送加入房间请求:', joinForm.roomId)
    wsClient.send('/app/room/join', {
      roomId: joinForm.roomId,
      password: joinForm.password
    })

    // 设置超时，如果没有收到响应则重置状态
    setTimeout(() => {
      if (joining.value) {
        console.warn('[Home] 加入房间超时')
        joining.value = false
        ElMessage.warning('加入房间超时，请重试')
      }
    }, 5000)
  } catch (error) {
    console.error('[Home] 加入房间失败:', error)
    ElMessage.error('加入房间失败')
    joining.value = false
  }
}

// 取消加入房间
const handleCancelJoinRoom = () => {
  showJoinRoomDialog.value = false
  joinForm.roomId = ''
  joinForm.password = ''
  joining.value = false
}

// 加载公开房间
const loadPublicRooms = async () => {
  if (!userStore.isLoggedIn) {
    return
  }

  // 确保WebSocket已连接
  const token = localStorage.getItem('token')
  if (token && !wsClient.isConnected()) {
    try {
      await wsClient.connect(token)
    } catch (error) {
      console.error('[Home] WebSocket连接失败:', error)
      loadingPublicRooms.value = false
      return
    }
  }

  loadingPublicRooms.value = true
  wsClient.send('/app/room/list', {})
  // 3秒后停止加载状态
  setTimeout(() => {
    loadingPublicRooms.value = false
  }, 3000)
}

// 加入公开房间
const joinPublicRoom = async (roomId: string) => {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }

  // 确保WebSocket已连接
  const token = localStorage.getItem('token')
  if (token && !wsClient.isConnected()) {
    try {
      await wsClient.connect(token)
    } catch (error) {
      ElMessage.error('服务器连接失败，请重试')
      return
    }
  }

  // 直接加入，不需要密码
  joinForm.roomId = roomId
  handleConfirmJoinRoom()
}

// 订阅房间相关消息
const subscribeRoomMessages = async () => {
  // 确保用户信息是最新的
  if (!userStore.userInfo) {
    console.log('[Home] 用户信息不存在，正在初始化...')
    await userStore.initUserInfo()
  }

  // 获取当前用户ID
  const userId = userStore.userInfo?.id
  if (!userId) {
    console.warn('[Home] 用户ID不存在，无法订阅房间消息')
    return
  }

  console.log('[Home] 开始订阅房间消息，用户ID:', userId, '用户名:', userStore.userInfo?.username)

  // 订阅房间创建成功消息（直接订阅用户专属topic）
  const userTopic = `/topic/user/${userId}/room`
  roomCreatedSubId = wsClient.subscribe(userTopic, (data: any) => {
    console.log('[Home] ✅✅✅✅✅✅✅✅✅✅✅ 收到房间创建消息:', data)
    if (data.type === 'ROOM_CREATED' && data.success) {
      showCreateRoomDialog.value = false
      creating.value = false
      // 跳转到游戏页面
      router.push(`/game/${data.roomId}`)
    } else if (data.type === 'ROOM_ERROR') {
      ElMessage.error(data.message || '操作失败')
      creating.value = false
    }
  })
  console.log('[Home] 房间创建订阅完成, topic:', userTopic, 'ID:', roomCreatedSubId)

  // 订阅房间加入成功消息（使用同一topic）
  roomJoinedSubId = wsClient.subscribe(userTopic, (data: any) => {
    console.log('[Home] 收到房间加入消息:', data)
    if (data.type === 'ROOM_JOINED' && data.success) {
      showJoinRoomDialog.value = false
      joining.value = false
      // 跳转到游戏页面
      router.push(`/game/${data.roomId}`)
    } else if (data.type === 'ROOM_ERROR') {
      ElMessage.error(data.message || '操作失败')
      showJoinRoomDialog.value = false
      joining.value = false
    }
  })
  console.log('[Home] 房间加入订阅完成, topic:', userTopic, 'ID:', roomJoinedSubId)

  // 订阅房间错误消息
  roomErrorSubId = wsClient.subscribe(userTopic, (data: any) => {
    console.log('[Home] ❌❌❌❌❌❌❌❌❌ 收到房间错误消息:', data)
    if (data.type === 'ROOM_ERROR') {
      ElMessage.error(data.message || '操作失败')
      creating.value = false
      joining.value = false
      loadingPublicRooms.value = false
    }
  })
  console.log('[Home] 房间错误订阅完成, topic:', userTopic, 'ID:', roomErrorSubId)

  // 订阅房间列表更新
  roomListSubId = wsClient.subscribe('/topic/rooms/public', (data: any) => {
    console.log('[Home] 收到房间列表:', data)
    publicRooms.value = data.rooms || []
    loadingPublicRooms.value = false
  })
  console.log('[Home] 房间列表订阅完成, ID:', roomListSubId)

  // 订阅观战房间列表（使用 /user 前缀，Stomp 会自动处理用户会话）
  const observerTopic = `/user/queue/observer/rooms`
  console.log('[Home] 准备订阅观战房间列表，topic:', observerTopic, '当前用户ID:', userId)
  observableRoomsSubId = wsClient.subscribe(observerTopic, (data: any) => {
    console.log('[Home] 📨📨📨 收到观战房间列表消息:', data)
    console.log('[Home] 消息类型:', data?.type)
    console.log('[Home] 房间数据:', data?.rooms)
    if (data.type === 'OBSERVABLE_ROOMS') {
      const rooms = data.rooms || []
      console.log('[Home] 处理观战房间，房间数量:', rooms.length)
      liveGames.value = rooms.map((room: any) => {
        console.log('[Home] 处理房间:', room)
        return {
          id: room.id,
          blackPlayer: room.player1?.username || room.player1?.nickname || '黑方',
          whitePlayer: room.player2?.username || room.player2?.nickname || '白方',
          mode: room.mode,
          moveCount: 0
        }
      })
      console.log('[Home] 更新后的 liveGames:', liveGames.value)
      console.log('[Home] liveGames.length:', liveGames.value.length)
    } else {
      console.log('[Home] 收到的消息类型不是 OBSERVABLE_ROOMS，而是:', data?.type)
      console.log('[Home] 完整数据:', JSON.stringify(data))
    }
  })
  console.log('[Home] 观战房间列表订阅完成, topic:', observerTopic, 'ID:', observableRoomsSubId)

  // 订阅私聊消息（全局订阅，始终接收私聊消息）
  // 使用类似房间聊天的 topic 方式
  if (userStore.userInfo?.id) {
    const privateChatTopic = '/topic/chat/private/' + userStore.userInfo.id
    console.log('[Home] 准备订阅私聊消息，topic:', privateChatTopic)
    globalPrivateChatSubId = wsClient.subscribe(privateChatTopic, (data: any) => {
      console.log('[Home] 💬 收到私聊消息:', data)

      if (data.type === 'PRIVATE_MESSAGE') {
        const senderId = data.senderId
        const receiverId = data.receiverId
        const currentUserId = userStore.userInfo?.id

        // 如果聊天对话框打开且消息来自当前聊天好友
        if (showChatDialog.value && chatFriend.value) {
          if (senderId === chatFriend.value.userId || receiverId === chatFriend.value.userId) {
            // 如果是自己发送的消息，检查是否有临时消息需要替换
            if (senderId === currentUserId) {
              const tempIndex = chatMessages.value.findIndex(m => m.tempId !== undefined)
              if (tempIndex !== -1) {
                // 替换临时消息
                const time = formatMessageTime(data.createdAt)
                chatMessages.value[tempIndex] = {
                  sender: data.senderNickname || data.senderUsername || '我',
                  content: data.content,
                  time,
                  isMine: true
                }
                console.log('[Home] 💬 替换临时消息')
                return
              }
            }

            // 添加新消息
            const senderName = data.senderNickname || data.senderUsername || '好友'
            const isMine = senderId === currentUserId
            const time = formatMessageTime(data.createdAt)
            addChatMessage(senderName, data.content, isMine, time)
          }
        }

        // 如果是收到消息（不是自己发的），显示通知
        if (senderId !== currentUserId) {
          const senderName = data.senderNickname || data.senderUsername || '好友'
          ElMessage.info(`收到 ${senderName} 的消息: ${data.content}`)
        }
      }
    })
    console.log('[Home] 私聊消息订阅完成, topic:', privateChatTopic, 'ID:', globalPrivateChatSubId)

    // 订阅聊天历史响应
    console.log('[Home] ========== 开始订阅聊天历史 ==========')
    try {
      // 使用 topic-based 订阅（与私聊消息相同的模式）
      const userId = userStore.userInfo?.id
      const chatHistoryTopic = `/topic/chat/history/${userId}`
      console.log('[Home] 准备订阅聊天历史，topic:', chatHistoryTopic)
      console.log('[Home] WebSocket连接状态:', wsClient.isConnected())
      console.log('[Home] 开始调用 wsClient.subscribe...')

      globalChatHistorySubId = wsClient.subscribe(chatHistoryTopic, (data: any) => {
        console.log('[Home] 📜 收到聊天历史响应:', data)
        console.log('[Home] 📜 响应类型:', data.type)
        console.log('[Home] 📜 响应数据:', JSON.stringify(data))
        if (data.type === 'CHAT_HISTORY') {
          const friendId = data.friendId
          const historyMessages = data.messages || []
          console.log('[Home] 📜 聊天历史好友ID:', friendId, ', 消息数:', historyMessages.length)

          // 🔧 始终缓存聊天历史（保存原始顺序，从旧到新）
          chatHistoryCache.value.set(friendId, historyMessages)
          console.log('[Home] 💾 聊天历史已缓存: friendId=', friendId, ', 消息数:', historyMessages.length)

          // 如果当前对话框打开且好友匹配，则加载历史
          if (showChatDialog.value && chatFriend.value && friendId === chatFriend.value.userId) {
            console.log('[Home] 📜 当前聊天对话框匹配，加载聊天历史')
            loadChatHistoryFromCache(friendId)
          } else {
            console.log('[Home] ⚠️ 聊天对话框未打开或好友不匹配，仅缓存历史')
            console.log('[Home] ⚠️ showChatDialog:', showChatDialog.value, ', chatFriend.userId:', chatFriend.value?.userId)
          }
        }
      })

      console.log('[Home] 聊天历史订阅完成, topic:', chatHistoryTopic, 'ID:', globalChatHistorySubId)
      if (!globalChatHistorySubId) {
        console.error('[Home] ❌❌❌ 聊天历史订阅失败！返回空ID')
      } else {
        console.log('[Home] ✅ 聊天历史订阅成功！ID:', globalChatHistorySubId)
      }
    } catch (error) {
      console.error('[Home] ❌ 订阅聊天历史时发生错误:', error)
    }
    console.log('[Home] ========== 聊天历史订阅流程结束 ==========')
  }

  console.log('[Home] 所有房间消息订阅完成')
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

  // 如果URL参数是观战面板，加载房间列表
  if (route.query.panel === 'spectate') {
    setTimeout(() => loadObservableRooms(), 200)
  }

  // 检查 URL 参数，初始化正确的面板
  const panel = route.query.panel as string
  const mode = route.query.mode as string

  if (panel) {
    const validPanels = ['pve', 'quick-match', 'room', 'spectate', 'friends', 'rank', 'records', 'help']
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
  fetchPendingRequests()
  fetchRecommendations()

  // 加载对局记录
  fetchRecords()

  // 连接WebSocket并加载房间列表
  if (userStore.isLoggedIn) {
    connectWebSocketAndSubscribe()
  }

  // 初始化粒子效果
  initParticleEffects()

  // 启动好友申请定时刷新（每30秒）
  friendRequestRefreshInterval = setInterval(() => {
    fetchPendingRequests()
  }, 30000)

  // 初始化横向导航的触摸滚动
  initHorizontalNavScroll()
})

// 监听面板切换，当切换到观战面板时加载房间列表
watch(activeNav, (newNav) => {
  console.log('[Home] activeNav 变化:', newNav)
  if (newNav === 'spectate' && userStore.isLoggedIn) {
    console.log('[Home] 切换到观战面板，加载房间列表')
    loadObservableRooms()
  } else if (newNav === 'records' && userStore.isLoggedIn) {
    console.log('[Home] 切换到对局记录面板，加载记录')
    fetchRecords()
    fetchRecordStats()
  } else if (newNav === 'rank' && userStore.isLoggedIn) {
    console.log('[Home] 切换到排行榜面板，加载排行榜')
    fetchRankList()
  } else if (newNav === 'friends' && userStore.isLoggedIn) {
    console.log('[Home] 切换到好友面板，加载好友列表')
    fetchFriends()
    fetchPendingRequests()
  }
})

// 监听好友标签切换，当切换到好友申请或推荐时刷新列表
watch(activeFriendsTab, (newTab) => {
  if (newTab === 'requests' && userStore.isLoggedIn) {
    fetchPendingRequests()
  } else if (newTab === 'recommendations' && userStore.isLoggedIn) {
    fetchRecommendations()
  }
})

// 连接WebSocket并订阅房间消息
const connectWebSocketAndSubscribe = async () => {
  const token = localStorage.getItem('token')
  if (!token) return

  try {
    // 先确保WebSocket已连接
    if (!wsClient.isConnected()) {
      console.log('[Home] WebSocket未连接，开始连接...')

      // 🔧 清除所有旧的订阅ID（因为可能是重新连接）
      globalChatHistorySubId = ''
      globalPrivateChatSubId = ''
      roomCreatedSubId = ''
      roomJoinedSubId = ''
      roomErrorSubId = ''
      roomListSubId = ''
      observableRoomsSubId = ''
      console.log('[Home] 🔧 清除所有旧订阅ID')

      await wsClient.connect(token)
      console.log('[Home] WebSocket连接成功')
    }

    // 等待一小段时间确保连接稳定
    await new Promise(resolve => setTimeout(resolve, 100))

    // 然后订阅房间消息（等待用户信息初始化完成）
    await subscribeRoomMessages()

    // 再等待一小段时间确保订阅完成
    await new Promise(resolve => setTimeout(resolve, 100))

    console.log('[Home] 所有订阅完成，聊天历史订阅ID:', globalChatHistorySubId)

    // 加载房间列表
    loadPublicRooms()
  } catch (error) {
    console.error('[Home] WebSocket连接失败:', error)
  }
}

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
  // 清理房间订阅
  if (roomCreatedSubId) wsClient.unsubscribe(roomCreatedSubId)
  if (roomJoinedSubId) wsClient.unsubscribe(roomJoinedSubId)
  if (roomErrorSubId) wsClient.unsubscribe(roomErrorSubId)
  if (roomListSubId) wsClient.unsubscribe(roomListSubId)
  if (observableRoomsSubId) wsClient.unsubscribe(observableRoomsSubId)

  // 清理全局私聊消息订阅
  if (globalPrivateChatSubId) wsClient.unsubscribe(globalPrivateChatSubId)

  // 清理好友申请定时刷新
  if (friendRequestRefreshInterval) {
    clearInterval(friendRequestRefreshInterval)
    friendRequestRefreshInterval = null
  }

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

/* 公开房间列表样式 */
.room-list-section {
  background: white;
  border-radius: 16px;
  padding: 25px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  margin-top: 20px;
}

.room-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.room-list-header h3 {
  font-size: 18px;
  color: #ff6b35;
  margin: 0;
}

.room-list-items {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 350px;
  overflow-y: auto;
}

.room-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: linear-gradient(135deg, #f8f9fa 0%, #e9ecef 100%);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.room-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.room-icon {
  font-size: 28px;
  width: 45px;
  height: 45px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 10px;
}

.room-info {
  flex: 1;
}

.room-name {
  font-weight: 600;
  color: #333;
  margin-bottom: 4px;
  font-size: 15px;
}

.room-id {
  font-size: 13px;
  color: #666;
  margin-bottom: 4px;
  font-family: 'Courier New', monospace;
}

.room-meta {
  font-size: 12px;
  display: flex;
  gap: 10px;
}

.mode-tag {
  padding: 3px 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border-radius: 10px;
  font-weight: 500;
}

.player-count {
  color: #666;
}

.join-mini-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 15px;
  background: linear-gradient(135deg, #ff6b35, #ff8c61);
  color: white;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.3s;
}

.join-mini-btn:hover {
  transform: scale(1.05);
}

/* ========== 排行榜 ========== */
.rank-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 30px 0;
  padding-bottom: 15px;
  border-bottom: 2px solid #f0f0f0;
}

.rank-tabs {
  display: flex;
  gap: 10px;
  border-bottom: none;
  margin: 0;
  padding: 0;
}

.rank-tab-item {
  padding: 12px 24px;
  font-size: 16px;
  color: #666;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  margin-bottom: -17px;
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
  max-height: 350px;
  overflow-y: auto;
  padding-right: 8px;
}

/* 滚动条样式 */
.rank-items::-webkit-scrollbar {
  width: 6px;
}

.rank-items::-webkit-scrollbar-track {
  background: #f0f0f0;
  border-radius: 3px;
}

.rank-items::-webkit-scrollbar-thumb {
  background: #ccc;
  border-radius: 3px;
}

.rank-items::-webkit-scrollbar-thumb:hover {
  background: #aaa;
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
  color: #ff6b35;
  margin-right: 15px;
  background: linear-gradient(135deg, #fff5f0 0%, #ffe8dc 100%);
  border-radius: 50%;
}

.medal {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

/* 统一排行榜颜色主题 */

.player-level {
  color: #666;
  font-weight: 500;
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

.player-level {
  font-size: 14px;
  color: #999;
}

.player-stats {
  display: flex;
  gap: 15px;
  margin-left: auto;
}

.stat-item {
  text-align: center;
  padding: 8px 20px;
  border-radius: 8px;
  background: #f8f9fa;
  min-width: 100px;
}

.stat-label {
  font-size: 12px;
  color: #666;
  display: block;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 16px;
  font-weight: 600;
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

.my-rank-banner.no-rank {
  background: linear-gradient(135deg, #999 0%, #777 100%);
}

.my-rank-label {
  font-size: 14px;
}

.my-rank-value {
  font-size: 20px;
  font-weight: bold;
}

/* 对局记录面板样式 */
.records-container {
  padding: 20px;
}

.records-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
  margin-bottom: 20px;
}

.summary-item {
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
  padding: 15px;
  border-radius: 8px;
  text-align: center;
}

.summary-value {
  font-size: 24px;
  font-weight: bold;
  color: #333;
}

.summary-label {
  font-size: 12px;
  color: #666;
  margin-top: 5px;
}

.records-filter {
  margin-bottom: 15px;
  display: flex;
  justify-content: center;
}

.records-filter :deep(.el-radio-group) {
  display: flex;
  gap: 8px;
}

.records-filter :deep(.el-radio-button__inner) {
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid #ddd;
  background: white;
  color: #666;
  transition: all 0.3s;
}

.records-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: linear-gradient(135deg, #a8edea 0%, #fed6e3 100%);
  border-color: #a8edea;
  color: #333;
}

.records-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 500px;
  overflow-y: auto;
  padding-right: 8px;
}

.records-list::-webkit-scrollbar {
  width: 6px;
}

.records-list::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

.records-list::-webkit-scrollbar-thumb {
  background: #888;
  border-radius: 3px;
}

.records-list::-webkit-scrollbar-thumb:hover {
  background: #555;
}

.empty-records {
  text-align: center;
  padding: 40px 20px;
  color: #999;
}

.record-item {
  background: white;
  border-radius: 8px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.3s;
  border-left: 4px solid #ddd;
}

.record-item:hover {
  transform: translateX(5px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.record-item.win {
  border-left-color: #52c41a;
}

.record-item.loss {
  border-left-color: #ff4d4f;
}

.record-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.record-result {
  font-size: 16px;
  font-weight: bold;
}

.result-win {
  color: #52c41a;
}

.result-loss {
  color: #ff4d4f;
}

.result-draw {
  color: #999;
}

.record-time {
  font-size: 12px;
  color: #999;
}

.record-players {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.player {
  display: flex;
  align-items: center;
  gap: 5px;
}

.player.winner {
  font-weight: bold;
  color: #52c41a;
}

.vs {
  font-size: 12px;
  color: #999;
}

.record-info {
  display: flex;
  gap: 10px;
  font-size: 12px;
}

.info-item {
  padding: 2px 6px;
  background: #f5f5f5;
  border-radius: 4px;
  color: #666;
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

/* ========== 推荐好友 ========== */
.recommendations-section {
  padding: 20px 0;
}

.recommendation-items {
  display: flex;
  flex-direction: column;
  gap: 15px;
  margin-top: 20px;
}

.recommendation-card {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  background: #fafafa;
  border-radius: 12px;
  transition: all 0.3s;
}

.recommendation-card:hover {
  background: #f0f0f0;
  transform: translateX(5px);
}

.recommendation-avatar {
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

.recommendation-info {
  flex: 1;
}

.recommendation-name {
  font-size: 16px;
  font-weight: 500;
  color: #333;
  margin-bottom: 5px;
}

.recommendation-details {
  display: flex;
  gap: 10px;
  font-size: 14px;
}

.recommendation-details .level-badge {
  background: linear-gradient(135deg, #ff6b35 0%, #ff8c61 100%);
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

.recommendation-details .rating-badge {
  color: #666;
}

.recommendation-details .online-badge {
  background: #67c23a;
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 12px;
}

/* 好友操作按钮区域 */
.friend-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.friend-actions .el-button {
  margin: 0;
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
    justify-content: center;
    flex-wrap: wrap;
    gap: 10px;
  }

  .user-profile-display {
    width: 100%;
    justify-content: center;
    text-align: center;
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

  /* 排行榜移动端优化 */
  .rank-item {
    padding: 15px;
    flex-wrap: wrap;
  }

  .rank-number {
    width: 40px;
    height: 40px;
    font-size: 16px;
    margin-right: 12px;
  }

  .avatar-circle {
    width: 40px;
    height: 40px;
    font-size: 16px;
  }

  .player-name {
    font-size: 16px;
  }

  .player-level {
    font-size: 12px;
  }

  .player-stats {
    gap: 10px;
    margin-left: 0;
    width: 100%;
    justify-content: flex-start;
  }

  .stat-item {
    padding: 6px 12px;
    min-width: 70px;
  }

  .stat-label {
    font-size: 11px;
  }

  .stat-value {
    font-size: 14px;
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

  .footer-brand {
    justify-content: center;
    text-align: center;
  }

  .footer-links {
    grid-template-columns: repeat(2, 1fr);
    gap: 30px;
  }

  .footer-link-section {
    text-align: center;
  }

  .footer-stats {
    align-items: center;
  }

  .footer-stat-item {
    width: 100%;
    max-width: 280px;
    justify-content: center;
  }

  .footer-bottom {
    flex-direction: column;
    gap: 15px;
    text-align: center;
  }

  .footer-social {
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .footer-container {
    padding: 30px 15px 15px;
  }

  .footer-brand {
    flex-direction: column;
    gap: 10px;
  }

  .footer-logo {
    width: 50px;
    height: 50px;
    font-size: 24px;
  }

  .footer-brand-text h3 {
    font-size: 16px;
  }

  .footer-brand-text p {
    font-size: 13px;
  }

  .footer-links {
    grid-template-columns: 1fr;
    gap: 20px;
  }

  .footer-link-section h4 {
    font-size: 13px;
  }

  .footer-link-section a {
    font-size: 14px;
  }

  .footer-stat-value {
    font-size: 18px;
  }

  .footer-stat-label {
    font-size: 11px;
  }

  .footer-copyright {
    font-size: 12px;
  }

  /* 排行榜小屏幕优化 */
  .rank-item {
    padding: 12px;
  }

  .rank-number {
    width: 36px;
    height: 36px;
    font-size: 14px;
    margin-right: 10px;
  }

  .medal {
    font-size: 18px;
  }

  .avatar-circle {
    width: 36px;
    height: 36px;
    font-size: 14px;
    margin-right: 10px;
  }

  .player-name {
    font-size: 14px;
  }

  .player-level {
    font-size: 11px;
  }

  .player-stats {
    gap: 8px;
  }

  .stat-item {
    padding: 5px 10px;
    min-width: 60px;
  }

  .stat-label {
    font-size: 10px;
  }

  .stat-value {
    font-size: 13px;
  }

  .me-tag {
    font-size: 10px;
    padding: 1px 6px;
  }
}

/* 聊天对话框样式 */
.chat-dialog :deep(.el-dialog__header) {
  padding: 0;
}

.chat-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.chat-dialog :deep(.el-dialog__footer) {
  display: none;
}

.chat-dialog-header {
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.chat-dialog-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.chat-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
}

.chat-status.online {
  background: rgba(82, 196, 26, 0.2);
  color: #52c41a;
}

.chat-status.offline {
  background: rgba(153, 153, 153, 0.2);
  color: #999;
}

.chat-panel {
  background: white;
}

.chat-messages {
  height: 300px;
  overflow-y: auto;
  padding: 12px;
  background: #f9f9f9;
}

.chat-empty {
  text-align: center;
  color: #999;
  padding: 40px 20px;
  font-size: 14px;
}

.chat-message {
  margin-bottom: 12px;
  max-width: 80%;
}

.chat-message.my-message {
  margin-left: auto;
}

.message-sender {
  font-size: 12px;
  color: #666;
  margin-bottom: 4px;
}

.chat-message.my-message .message-sender {
  text-align: right;
}

.message-content {
  padding: 8px 12px;
  border-radius: 8px;
  word-break: break-word;
  background: white;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.chat-message.my-message .message-content {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: white;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.chat-message.my-message .message-time {
  text-align: right;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 12px;
  background: white;
  border-top: 1px solid #eee;
}

.chat-input .el-input {
  flex: 1;
}

/* 复盘对话框 */
.replay-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.replay-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.replay-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
}

.replay-players {
  display: flex;
  gap: 20px;
  font-size: 16px;
}

.replay-player {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-icon {
  font-size: 20px;
}

.replay-result {
  font-size: 18px;
  font-weight: bold;
}

.replay-record-id {
  font-size: 12px;
  color: #999;
  margin-left: auto;
}

.no-moves-warning {
  text-align: center;
  padding: 40px 20px;
  background: #fff3cd;
  border: 1px solid #ffc107;
  border-radius: 8px;
  color: #856404;
  font-size: 16px;
  margin-bottom: 20px;
}

.replay-board {
  display: flex;
  justify-content: center;
  padding: 20px;
  background: #f5f5f5;
  border-radius: 8px;
}

.replay-controls {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: #f5f5f5;
  border-radius: 8px;
}

.move-indicator {
  margin-left: auto;
  font-size: 14px;
  font-weight: bold;
  color: #333;
}
</style>
