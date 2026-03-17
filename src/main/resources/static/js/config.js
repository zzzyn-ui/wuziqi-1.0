/**
 * 前端配置管理
 * 统一管理WebSocket URL等配置项
 */

const AppConfig = {
    /**
     * 获取WebSocket URL
     * 动态根据当前页面协议和主机生成WebSocket连接地址
     * @param {string} token - 可选的认证token
     * @returns {string} WebSocket URL
     */
    getWebSocketUrl: function(token) {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const host = window.location.hostname;
        const port = '9090'; // WebSocket 端口
        const path = '/ws';
        let url = `${protocol}//${host}:${port}${path}`;

        // 如果提供了token，将其作为查询参数
        if (token) {
            url += `?token=${encodeURIComponent(token)}`;
        }

        return url;
    },

    /**
     * 获取API基础URL
     * @returns {string} API基础URL
     */
    getApiBaseUrl: function() {
        return `${window.location.protocol}//${window.location.host}/api`;
    },

    /**
     * 获取当前用户信息
     * @returns {object|null} 用户信息对象或null
     */
    getCurrentUser: function() {
        const userStr = localStorage.getItem('currentUser');
        if (!userStr) return null;

        try {
            return JSON.parse(userStr);
        } catch (e) {
            console.error('Failed to parse currentUser:', e);
            return null;
        }
    },

    /**
     * 获取认证token
     * @returns {string|null} token或null
     */
    getAuthToken: function() {
        return localStorage.getItem('authToken') || localStorage.getItem('gobang_token');
    },

    /**
     * 设置认证token
     * @param {string} token - token值
     */
    setAuthToken: function(token) {
        localStorage.setItem('authToken', token);
        localStorage.setItem('gobang_token', token);
    },

    /**
     * 清除认证信息
     */
    clearAuth: function() {
        localStorage.removeItem('authToken');
        localStorage.removeItem('gobang_token');
        localStorage.removeItem('currentUser');
    },

    /**
     * 安全地设置文本内容（防止XSS）
     * @param {HTMLElement} element - 目标元素
     * @param {string} text - 文本内容
     */
    safeSetText: function(element, text) {
        if (element && text !== undefined && text !== null) {
            element.textContent = String(text);
        }
    },

    /**
     * 安全地创建元素并设置文本内容（防止XSS）
     * @param {string} tagName - 标签名
     * @param {string} className - 类名
     * @param {string} text - 文本内容
     * @returns {HTMLElement} 创建的元素
     */
    safeCreateElement: function(tagName, className, text) {
        const element = document.createElement(tagName);
        if (className) {
            element.className = className;
        }
        if (text !== undefined && text !== null) {
            element.textContent = String(text);
        }
        return element;
    },

    /**
     * 格式化日期时间
     * @param {number|string|Date} date - 日期
     * @returns {string} 格式化的日期时间字符串
     */
    formatDateTime: function(date) {
        const d = new Date(date);
        return d.toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
    },

    /**
     * 格式化时间差（如：5分钟前）
     * @param {number|string|Date} date - 日期
     * @returns {string} 格式化的时间差字符串
     */
    formatTimeAgo: function(date) {
        const d = new Date(date);
        const now = new Date();
        const diff = Math.floor((now - d) / 1000); // 秒

        if (diff < 60) return '刚刚';
        if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`;
        if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`;
        if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`;
        return this.formatDateTime(date);
    }
};

// 将配置暴露到全局
if (typeof window !== 'undefined') {
    window.AppConfig = AppConfig;
}
