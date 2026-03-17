/**
 * API客户端
 * 提供调用后端API的接口
 *
 * 配置：确保后端服务器已启动API接口
 */
class ApiClient {

    /**
     * API基础URL
     * 自动根据当前页面地址生成
     */
    get baseUrl() {
        const protocol = window.location.protocol;
        const host = window.location.host;
        return `${protocol}//${host}/api`;
    }

    /**
     * 获取认证token
     */
    getAuthToken() {
        return localStorage.getItem('authToken') || localStorage.getItem('gobang_token');
    }

    /**
     * 通用请求方法
     */
    async request(endpoint, options = {}) {
        const url = `${this.baseUrl}${endpoint}`;
        const token = this.getAuthToken();

        const defaultOptions = {
            headers: {
                'Content-Type': 'application/json',
                ...(token && { 'Authorization': `Bearer ${token}` })
            }
        };

        const response = await fetch(url, { ...defaultOptions, ...options });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || '请求失败');
        }

        return response.json();
    }

    /**
     * GET请求
     */
    async get(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'GET' });
    }

    /**
     * POST请求
     */
    async post(endpoint, data, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: 'POST',
            body: JSON.stringify(data)
        });
    }

    /**
     * PUT请求
     */
    async put(endpoint, data, options = {}) {
        return this.request(endpoint, {
            ...options,
            method: 'PUT',
            body: JSON.stringify(data)
        });
    }

    /**
     * DELETE请求
     */
    async delete(endpoint, options = {}) {
        return this.request(endpoint, { ...options, method: 'DELETE' });
    }

    // ==================== 用户设置 API ====================

    /**
     * 获取用户设置
     */
    async getSettings() {
        return this.get('/settings');
    }

    /**
     * 更新用户设置
     */
    async updateSettings(settings) {
        return this.put('/settings', settings);
    }

    /**
     * 更新单个设置项
     */
    async updateSetting(key, value) {
        return this.updateSettings({ [key]: value });
    }

    // ==================== 活动日志 API ====================

    /**
     * 获取用户活动历史
     */
    async getActivityHistory(limit = 20) {
        return this.get(`/activity/history?limit=${limit}`);
    }

    // ==================== 对局收藏 API ====================

    /**
     * 获取用户收藏列表
     */
    async getFavorites() {
        return this.get('/favorites');
    }

    /**
     * 添加收藏
     */
    async addFavorite(gameRecordId, note = '', tags = '') {
        return this.post('/favorites', { gameRecordId, note, tags });
    }

    /**
     * 取消收藏
     */
    async removeFavorite(gameRecordId) {
        return this.delete(`/favorites/${gameRecordId}`);
    }

    /**
     * 检查是否已收藏
     */
    async checkFavorited(gameRecordId) {
        return this.get(`/favorites/check/${gameRecordId}`);
    }

    /**
     * 更新收藏备注
     */
    async updateFavorite(gameRecordId, note, tags) {
        return this.put(`/favorites/${gameRecordId}`, { note, tags });
    }

    // ==================== 游戏邀请 API ====================

    /**
     * 获取待处理邀请
     */
    async getPendingInvitations() {
        return this.get('/invitations/pending');
    }

    /**
     * 获取发送的邀请
     */
    async getSentInvitations(limit = 20) {
        return this.get(`/invitations/sent?limit=${limit}`);
    }

    /**
     * 发送游戏邀请
     */
    async sendInvitation(inviteeId, invitationType = 'casual') {
        return this.post('/invitations', { inviteeId, invitationType });
    }

    /**
     * 接受邀请
     */
    async acceptInvitation(invitationId, roomId) {
        return this.post(`/invitations/${invitationId}/accept`, { roomId });
    }

    /**
     * 拒绝邀请
     */
    async rejectInvitation(invitationId) {
        return this.post(`/invitations/${invitationId}/reject`);
    }

    /**
     * 取消邀请
     */
    async cancelInvitation(invitationId) {
        return this.delete(`/invitations/${invitationId}`);
    }

    // ==================== 辅助方法 ====================

    /**
     * 处理API错误
     */
    handleError(error, showMessage = true) {
        console.error('API Error:', error);

        if (showMessage) {
            const message = error.message || '操作失败，请稍后重试';
            // 可以替换为项目中的提示组件
            alert(message);
        }

        throw error;
    }

    /**
     * 安全地执行API调用
     */
    async safeExecute(apiCall, errorCallback) {
        try {
            return await apiCall();
        } catch (error) {
            if (errorCallback) {
                errorCallback(error);
            } else {
                this.handleError(error);
            }
            throw error;
        }
    }
}

// 创建全局API客户端实例
const apiClient = new ApiClient();

// 将API客户端暴露到全局
if (typeof window !== 'undefined') {
    window.ApiClient = ApiClient;
    window.api = apiClient;
}
