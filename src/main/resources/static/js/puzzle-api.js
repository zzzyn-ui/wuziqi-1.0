/**
 * 残局API客户端
 * 负责与后端API通信，获取残局数据、保存进度等
 */

class PuzzleApiClient {
    constructor() {
        this.apiBaseUrl = window.location.origin + '/api/puzzles';
        this.token = localStorage.getItem('token') || sessionStorage.getItem('token');
    }

    /**
     * 获取请求头
     */
    getHeaders() {
        const headers = {
            'Content-Type': 'application/json'
        };
        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token}`;
        }
        return headers;
    }

    /**
     * 处理API响应
     */
    async handleResponse(response) {
        if (!response.ok) {
            const error = await response.json().catch(() => ({ message: '网络请求失败' }));
            throw new Error(error.message || `请求失败: ${response.status}`);
        }
        return response.json();
    }

    /**
     * 获取残局列表
     * @param {string} difficulty - 难度级别 (easy, medium, hard, expert)
     * @returns {Promise<Array>} 残局列表
     */
    async getPuzzles(difficulty = null) {
        try {
            const url = difficulty ? `${this.apiBaseUrl}/list?difficulty=${difficulty}` : this.apiBaseUrl;
            const response = await fetch(url, {
                method: 'GET',
                headers: this.getHeaders()
            });
            const result = await this.handleResponse(response);
            // API返回: {success: true, data: {puzzles: [...], count: N}}
            return result.data?.puzzles || [];
        } catch (error) {
            console.error('获取残局列表失败:', error);
            throw error;
        }
    }

    /**
     * 获取单个残局详情
     * @param {number} puzzleId - 残局ID
     * @returns {Promise<Object>} 残局详情
     */
    async getPuzzleById(puzzleId) {
        try {
            const response = await fetch(`${this.apiBaseUrl}/${puzzleId}`, {
                method: 'GET',
                headers: this.getHeaders()
            });
            const data = await this.handleResponse(response);
            return data.data;
        } catch (error) {
            console.error('获取残局详情失败:', error);
            throw error;
        }
    }

    /**
     * 记录残局尝试
     * @param {number} puzzleId - 残局ID
     * @returns {Promise<Object>} 操作结果
     */
    async recordAttempt(puzzleId) {
        try {
            const response = await fetch(`${this.apiBaseUrl}/${puzzleId}/attempt`, {
                method: 'POST',
                headers: this.getHeaders()
            });
            const data = await this.handleResponse(response);
            return data.data;
        } catch (error) {
            console.error('记录尝试失败:', error);
            // 不抛出错误，避免影响游戏流程
            return null;
        }
    }

    /**
     * 记录残局完成
     * @param {number} puzzleId - 残局ID
     * @param {number} moves - 使用的步数
     * @param {number} time - 使用的时间（秒）
     * @param {boolean} won - 是否胜利
     * @returns {Promise<Object>} 操作结果
     */
    async recordCompletion(puzzleId, moves, time, won) {
        try {
            const response = await fetch(`${this.apiBaseUrl}/${puzzleId}/complete`, {
                method: 'POST',
                headers: this.getHeaders(),
                body: JSON.stringify({ moves, time, won })
            });
            const data = await this.handleResponse(response);
            return data.data;
        } catch (error) {
            console.error('记录完成失败:', error);
            // 不抛出错误，避免影响游戏流程
            return null;
        }
    }

    /**
     * 获取用户的残局记录
     * @returns {Promise<Array>} 用户的残局记录列表
     */
    async getUserRecords() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/my`, {
                method: 'GET',
                headers: this.getHeaders()
            });
            const data = await this.handleResponse(response);
            return data.data || [];
        } catch (error) {
            console.error('获取用户记录失败:', error);
            return [];
        }
    }

    /**
     * 获取残局统计信息
     * @returns {Promise<Object>} 统计信息
     */
    async getStats() {
        try {
            const response = await fetch(`${this.apiBaseUrl}/stats/summary`, {
                method: 'GET',
                headers: this.getHeaders()
            });
            const data = await this.handleResponse(response);
            return data.data || {};
        } catch (error) {
            console.error('获取统计信息失败:', error);
            return {};
        }
    }

    /**
     * 获取排行榜
     * @param {string} type - 排行榜类型 (all, difficulty, weekly)
     * @param {string} difficulty - 难度级别（可选）
     * @returns {Promise<Array>} 排行榜数据
     */
    async getLeaderboard(type = 'all', difficulty = null) {
        try {
            let url = `${this.apiBaseUrl}/stats/leaderboard?type=${type}`;
            if (difficulty) {
                url += `&difficulty=${difficulty}`;
            }
            const response = await fetch(url, {
                method: 'GET',
                headers: this.getHeaders()
            });
            const data = await this.handleResponse(response);
            return data.data || [];
        } catch (error) {
            console.error('获取排行榜失败:', error);
            return [];
        }
    }

    /**
     * 检查API是否可用
     * @returns {Promise<boolean>} API是否可用
     */
    async checkApiAvailable() {
        try {
            const response = await fetch(this.apiBaseUrl, {
                method: 'GET',
                headers: this.getHeaders(),
                timeout: 3000
            });
            return response.ok || response.status !== 404;
        } catch (error) {
            return false;
        }
    }
}

// 创建全局实例
window.puzzleApi = new PuzzleApiClient();
