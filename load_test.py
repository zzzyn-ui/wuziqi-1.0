#!/usr/bin/env python3
"""
五子棋压力测试客户端 (Python版本)
模拟100+用户同时在线，进行注册、登录、匹配、游戏等操作
"""

import asyncio
import websockets
import json
import time
import random
import logging
from datetime import datetime
from collections import defaultdict
from typing import Dict, List, Set, Optional
import threading
import statistics
import os
import sys

# 尝试导入psutil用于性能监控
try:
    import psutil
    PSUTIL_AVAILABLE = True
except ImportError:
    PSUTIL_AVAILABLE = False
    print("警告: psutil未安装，性能监控功能将不可用。")
    print("安装命令: pip install psutil")

# 配置
SERVER_URL = "ws://localhost:9091/ws"
TOTAL_USERS = 100
CONNECTION_BATCH_SIZE = 10
CONNECTION_BATCH_DELAY = 0.5  # 秒
TEST_DURATION = 300  # 秒

# 注册配置
AUTO_REGISTER = True  # 自动注册新用户
REGISTER_FIRST_USER = True  # 第一个用户先注册

# 日志配置
logging.basicConfig(
    level=logging.DEBUG,  # Changed to DEBUG for more details
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler(f'load_test_{datetime.now().strftime("%Y%m%d_%H%M%S")}.log')
    ]
)
logger = logging.getLogger(__name__)

# 性能统计
class Stats:
    def __init__(self):
        self.online_users = 0
        self.successful_logins = 0
        self.failed_logins = 0
        self.successful_registers = 0
        self.failed_registers = 0
        self.matched_games = 0
        self.active_games = 0
        self.completed_games = 0
        self.total_messages_sent = 0
        self.total_messages_received = 0
        self.login_latencies = []
        self.register_latencies = []
        self.match_latencies = []
        self.move_latencies = []
        self.errors = defaultdict(int)
        self.lock = threading.Lock()

    def increment(self, field: str, value: int = 1):
        with self.lock:
            setattr(self, field, getattr(self, field) + value)

    def add_latency(self, field: str, value: float):
        with self.lock:
            # Handle both naming conventions: login_latency -> login_latencies
            # Replace 'y' with 'ies' for words ending in 'y'
            if field.endswith('y'):
                attr_name = field[:-1] + 'ies'  # latency -> latencies
            else:
                attr_name = field + 's'
            getattr(self, attr_name).append(value)

    def record_error(self, error_type: str):
        with self.lock:
            self.errors[error_type] += 1

    def get_percentile(self, latencies: List[float], percentile: float) -> float:
        """计算延迟分位数"""
        if not latencies:
            return 0.0
        sorted_latencies = sorted(latencies)
        index = int(len(sorted_latencies) * percentile / 100)
        return sorted_latencies[min(index, len(sorted_latencies) - 1)]

stats = Stats()

# 性能监控器
class PerformanceMonitor:
    def __init__(self):
        self.running = False
        self.thread = None
        self.cpu_history = []
        self.memory_history = []
        self.start_time = None

    def start(self):
        """启动性能监控"""
        if not PSUTIL_AVAILABLE:
            return
        self.running = True
        self.start_time = time.time()
        self.thread = threading.Thread(target=self._monitor_loop, daemon=True)
        self.thread.start()

    def _monitor_loop(self):
        """监控循环"""
        process = psutil.Process(os.getpid())
        sample_interval = 1.0  # 每秒采样一次

        while self.running:
            try:
                # CPU使用率 (需要间隔采样)
                cpu_percent = process.cpu_percent(interval=sample_interval)
                self.cpu_history.append(cpu_percent)

                # 内存使用
                memory_info = process.memory_info()
                memory_mb = memory_info.rss / 1024 / 1024  # 转换为MB
                self.memory_history.append(memory_mb)

            except Exception as e:
                logger.debug(f"性能监控错误: {e}")

    def stop(self):
        """停止性能监控"""
        self.running = False
        if self.thread:
            self.thread.join(timeout=2)

    def get_stats(self) -> Dict:
        """获取性能统计"""
        if not PSUTIL_AVAILABLE or not self.cpu_history:
            return {
                'cpu_avg': 0,
                'cpu_max': 0,
                'memory_avg': 0,
                'memory_max': 0,
                'memory_current': 0
            }

        return {
            'cpu_avg': statistics.mean(self.cpu_history),
            'cpu_max': max(self.cpu_history),
            'memory_avg': statistics.mean(self.memory_history),
            'memory_max': max(self.memory_history),
            'memory_current': self.memory_history[-1] if self.memory_history else 0
        }

perf_monitor = PerformanceMonitor()

class TestClient:
    """测试客户端类"""

    # 使用时间戳后6位生成唯一用户名，避免与数据库中现有用户冲突
    _test_id = str(int(time.time()))[-6:]

    def __init__(self, user_id: int):
        self.user_id = user_id
        # 使用简短的唯一ID确保每次测试运行都有唯一的用户名，同时保持长度在16字符以内
        # 格式: tu + 时间戳后6位 + 用户ID (最多3位) = 最多11个字符
        self.username = f"tu{TestClient._test_id}{min(user_id, 999)}"
        self.password = "testpass123"
        self.nickname = f"测试用户{user_id}"
        self.ws = None
        self.is_logged_in = False
        self.is_registered = False
        self.is_matching = False
        self.is_in_game = False
        self.is_black_player = False
        self.room_id = None
        self.board = [[0] * 15 for _ in range(15)]
        self.register_start_time = None
        self.login_start_time = None
        self.match_start_time = None
        self.move_start_time = None
        self.messages_sent = 0
        self.messages_received = 0
        self.user_id_from_server = None
        self.token = None
        # 所有用户都尝试注册
        self.should_register = AUTO_REGISTER

    async def connect(self):
        """连接到服务器"""
        try:
            self.ws = await websockets.connect(SERVER_URL)
            stats.increment('online_users')
            logger.debug(f"用户 {self.username} 连接成功")

            # 启动消息接收协程
            asyncio.create_task(self.receive_messages())

            # 连接成功后，根据配置决定是注册还是登录
            await asyncio.sleep(random.uniform(0.1, 0.5))
            if self.should_register and not self.is_registered:
                await self.send_register_request()
            else:
                await self.send_login_request()

        except Exception as e:
            logger.error(f"用户 {self.username} 连接失败: {e}")
            stats.increment('failed_logins')
            stats.record_error('connection_failed')

    async def receive_messages(self):
        """接收服务器消息"""
        try:
            async for message in self.ws:
                stats.total_messages_received += 1
                self.messages_received += 1

                # 解析消息
                await self.handle_message(message)

        except websockets.exceptions.ConnectionClosed:
            logger.info(f"用户 {self.username} 连接关闭")
            stats.increment('online_users', -1)
            if self.is_in_game:
                stats.increment('active_games', -1)

        except Exception as e:
            logger.error(f"用户 {self.username} 接收消息错误: {e}")

    async def handle_message(self, message: str):
        """处理服务器消息"""
        try:
            logger.debug(f"用户 {self.username} 收到消息: {message[:200]}...")

            # 尝试解析为JSON
            if isinstance(message, str):
                data = json.loads(message)
                msg_type = data.get('type')

                logger.debug(f"用户 {self.username} 解析消息类型: {msg_type}")

                # 认证响应
                if msg_type == 3:  # AUTH_RESPONSE
                    await self.handle_auth_response(data)
                # 匹配成功
                elif msg_type == 12:  # MATCH_SUCCESS
                    await self.handle_match_success(data)
                # 匹配失败
                elif msg_type == 13:  # MATCH_FAILED
                    await self.handle_match_failed(data)
                # 游戏状态
                elif msg_type == 22:  # GAME_STATE
                    await self.handle_game_state(data)
                # 游戏结束
                elif msg_type == 23:  # GAME_OVER
                    await self.handle_game_over(data)
                else:
                    logger.debug(f"用户 {self.username} 未处理的消息类型: {msg_type}")

        except json.JSONDecodeError as e:
            logger.error(f"用户 {self.username} JSON解析失败: {e}, 消息: {message[:200]}")
        except Exception as e:
            logger.error(f"用户 {self.username} 处理消息失败: {e}")

    async def send_login_request(self):
        """发送登录请求"""
        self.login_start_time = time.time()

        # 构造登录消息 - 使用AUTH_LOGIN(1)而不是TOKEN_AUTH(100)
        # 按照protobuf的LoginRequest格式构造body
        # 注意：body必须是JSON对象，不能是JSON字符串
        login_msg = {
            "type": 1,  # AUTH_LOGIN
            "sequence_id": int(time.time() * 1000000),
            "timestamp": int(time.time() * 1000),
            "body": {
                "username": self.username,
                "password": self.password
            }
        }

        await self.send_message(login_msg)
        logger.debug(f"用户 {self.username} 发送登录请求")

    async def send_register_request(self):
        """发送注册请求"""
        self.register_start_time = time.time()

        # 构造注册消息
        # 注意：body必须是JSON对象，不能是JSON字符串
        register_msg = {
            "type": 2,  # AUTH_REGISTER
            "sequence_id": int(time.time() * 1000000),
            "timestamp": int(time.time() * 1000),
            "body": {
                "username": self.username,
                "password": self.password,
                "nickname": self.nickname
            }
        }

        await self.send_message(register_msg)
        logger.debug(f"用户 {self.username} 发送注册请求")

    async def send_match_request(self):
        """发送匹配请求"""
        if self.is_matching or self.is_in_game:
            return

        self.is_matching = True
        self.match_start_time = time.time()

        # 注意：body必须是JSON对象，不能是JSON字符串
        match_msg = {
            "type": 10,  # MATCH_START
            "sequence_id": int(time.time() * 1000000),
            "timestamp": int(time.time() * 1000),
            "body": {
                "rating": 1200
            }
        }

        await self.send_message(match_msg)
        logger.debug(f"用户 {self.username} 请求匹配")

    async def send_move_request(self, x: int, y: int):
        """发送落子请求"""
        # 注意：body必须是JSON对象，不能是JSON字符串
        move_msg = {
            "type": 20,  # GAME_MOVE
            "sequence_id": int(time.time() * 1000000),
            "timestamp": int(time.time() * 1000),
            "body": {
                "x": x,
                "y": y
            }
        }

        await self.send_message(move_msg)

    async def send_message(self, msg: dict):
        """发送消息到服务器"""
        if self.ws:
            try:
                await self.ws.send(json.dumps(msg))
                stats.total_messages_sent += 1
                self.messages_sent += 1
            except Exception:
                # 连接已关闭
                pass

    async def handle_auth_response(self, data: dict):
        """处理认证响应（包括注册和登录）"""
        # 服务器返回的响应格式：{"type":3,"body":{"success":true,"message":"登录成功","token":"...","user_info":{...}}}
        # 需要从body中提取实际的响应数据
        body = data.get('body', {})
        if isinstance(body, str):
            body = json.loads(body)

        success = body.get('success', False) if body else False
        message = body.get('message', '') if body else ''
        token = body.get('token', '') if body else ''
        # 服务器返回user_info（snake_case），需要兼容userInfo（camelCase）
        user_info = body.get('user_info') or body.get('userInfo') or {} if body else {}

        # 根据之前的请求类型判断是注册还是登录响应
        # 检查login_start_time而不是register_start_time，因为注册失败后会尝试登录
        if self.login_start_time:
            # 登录响应
            latency = (time.time() - self.login_start_time) * 1000
            stats.add_latency('login_latency', latency)
            self.login_start_time = None  # 清除时间戳

            if success:
                self.is_logged_in = True
                self.token = token
                if user_info and 'userId' in user_info:
                    self.user_id_from_server = user_info['userId']
                stats.increment('successful_logins')
                logger.info(f"用户 {self.username} 登录成功，延迟: {latency:.2f}ms")

                # 登录成功后延迟一段时间开始匹配
                await asyncio.sleep(random.uniform(1, 4))
                await self.send_match_request()
            else:
                stats.increment('failed_logins')
                logger.warning(f"用户 {self.username} 登录失败: {message}")
                stats.record_error('login_failed')

        elif self.register_start_time and not self.is_registered:
            # 注册响应
            latency = (time.time() - self.register_start_time) * 1000
            stats.add_latency('register_latency', latency)
            self.register_start_time = None  # 清除时间戳

            if success:
                self.is_registered = True
                self.is_logged_in = True
                self.token = token
                if user_info and 'userId' in user_info:
                    self.user_id_from_server = user_info['userId']
                stats.increment('successful_registers')
                stats.increment('successful_logins')
                logger.info(f"用户 {self.username} 注册成功，延迟: {latency:.2f}ms")

                # 注册成功后延迟一段时间开始匹配
                await asyncio.sleep(random.uniform(1, 4))
                await self.send_match_request()
            else:
                stats.increment('failed_registers')
                logger.warning(f"用户 {self.username} 注册失败: {message}")
                # 注册失败，尝试登录
                await asyncio.sleep(1)
                await self.send_login_request()

    async def handle_match_success(self, data: dict):
        """处理匹配成功"""
        latency = (time.time() - self.match_start_time) * 1000
        self.is_matching = False
        self.is_in_game = True
        stats.increment('matched_games')
        stats.increment('active_games')
        stats.add_latency('match_latency', latency)

        # 解析房间信息
        if 'is_first' in str(data) or 'true' in str(data):
            self.is_black_player = True

        logger.info(f"用户 {self.username} 匹配成功，匹配延迟: {latency:.2f}ms")

        # 如果是黑棋，先手
        if self.is_black_player:
            await asyncio.sleep(0.5)
            await self.make_random_move()

    async def handle_match_failed(self, data: dict):
        """处理匹配失败"""
        self.is_matching = False
        logger.debug(f"用户 {self.username} 匹配失败")

        # 重新尝试匹配
        await asyncio.sleep(5)
        await self.send_match_request()

    async def handle_game_state(self, data: dict):
        """处理游戏状态"""
        if not self.is_in_game:
            return

        # 检查是否轮到己方下棋
        current_player = data.get('current_player')
        if (current_player == 1 and self.is_black_player) or \
           (current_player == 2 and not self.is_black_player):

            # 记录落子响应延迟
            if self.move_start_time:
                latency = (time.time() - self.move_start_time) * 1000
                stats.add_latency('move_latency', latency)

            await asyncio.sleep(random.uniform(0.5, 1.5))
            await self.make_random_move()

    async def handle_game_over(self, data: dict):
        """处理游戏结束"""
        self.is_in_game = False
        stats.increment('active_games', -1)
        stats.increment('completed_games')
        logger.info(f"用户 {self.username} 游戏结束")

        # 游戏结束后重新开始匹配
        await asyncio.sleep(random.uniform(2, 7))
        await self.send_match_request()

    async def make_random_move(self):
        """随机落子"""
        if not self.is_in_game:
            return

        # 记录落子开始时间
        self.move_start_time = time.time()

        # 找出所有空位
        empty_positions = []
        for i in range(15):
            for j in range(15):
                if self.board[i][j] == 0:
                    empty_positions.append((i, j))

        if empty_positions:
            x, y = random.choice(empty_positions)
            await self.send_move_request(x, y)
            self.board[x][y] = 1 if self.is_black_player else 2

    async def close(self):
        """关闭连接"""
        if self.ws:
            try:
                await self.ws.close()
            except Exception:
                pass


async def run_load_test():
    """运行压力测试"""
    # 启动性能监控
    perf_monitor.start()

    logger.info("=" * 40)
    logger.info("  五子棋压力测试开始")
    logger.info(f"  测试用户数: {TOTAL_USERS}")
    logger.info(f"  服务器地址: {SERVER_URL}")
    logger.info(f"  测试持续时间: {TEST_DURATION}秒")
    logger.info(f"  自动注册: {AUTO_REGISTER}")
    logger.info(f"  性能监控: {'启用' if PSUTIL_AVAILABLE else '禁用 (psutil未安装)'}")
    logger.info("=" * 40)

    clients: List[TestClient] = []

    # 定期输出统计信息
    stats_task = asyncio.create_task(print_stats_periodically())

    # 分批连接用户
    connected = 0
    while connected < TOTAL_USERS:
        batch_size = min(CONNECTION_BATCH_SIZE, TOTAL_USERS - connected)

        batch_clients = []
        for i in range(batch_size):
            user_id = connected + i + 1
            client = TestClient(user_id)
            clients.append(client)
            batch_clients.append(client)
            asyncio.create_task(client.connect())
            await asyncio.sleep(0.05)  # 避免同时连接

        connected += batch_size
        logger.info(f"已连接用户: {connected}/{TOTAL_USERS}")
        await asyncio.sleep(CONNECTION_BATCH_DELAY)

    logger.info("所有用户连接完成，开始压力测试...")

    # 等待测试完成
    await asyncio.sleep(TEST_DURATION)

    # 停止性能监控
    perf_monitor.stop()

    # 输出最终统计
    print_final_stats()

    # 关闭所有连接
    logger.info("正在关闭所有连接...")
    for client in clients:
        await client.close()

    stats_task.cancel()

    logger.info("压力测试完成")


async def print_stats_periodically():
    """定期输出统计信息"""
    while True:
        await asyncio.sleep(5)
        print_current_stats()


def print_current_stats():
    """打印当前统计信息"""
    avg_login_latency = (
        sum(stats.login_latencies) / len(stats.login_latencies)
        if stats.login_latencies else 0
    )

    perf_stats = perf_monitor.get_stats()

    logger.info("=" * 40)
    logger.info("  实时统计:")
    logger.info(f"  在线用户: {stats.online_users}/{TOTAL_USERS}")
    logger.info(f"  登录成功: {stats.successful_logins}, 失败: {stats.failed_logins}")
    if stats.successful_registers > 0 or stats.failed_registers > 0:
        logger.info(f"  注册成功: {stats.successful_registers}, 失败: {stats.failed_registers}")
    logger.info(f"  已完成对局: {stats.completed_games}, 进行中: {stats.active_games}")
    logger.info(f"  发送消息: {stats.total_messages_sent}, 接收消息: {stats.total_messages_received}")
    logger.info(f"  平均登录延迟: {avg_login_latency:.2f}ms")
    if PSUTIL_AVAILABLE:
        logger.info(f"  CPU使用: {perf_stats['cpu_avg']:.1f}% (最高: {perf_stats['cpu_max']:.1f}%)")
        logger.info(f"  内存使用: {perf_stats['memory_current']:.1f}MB (平均: {perf_stats['memory_avg']:.1f}MB, 最高: {perf_stats['memory_max']:.1f}MB)")
    logger.info("=" * 40)


def print_final_stats():
    """打印最终统计"""
    # 计算延迟统计
    def calc_latency_stats(latencies):
        if not latencies:
            return {'avg': 0, 'p50': 0, 'p95': 0, 'p99': 0, 'min': 0, 'max': 0}
        sorted_lat = sorted(latencies)
        return {
            'avg': statistics.mean(latencies),
            'p50': sorted_lat[int(len(latencies) * 0.5)],
            'p95': sorted_lat[int(len(latencies) * 0.95)],
            'p99': sorted_lat[int(len(latencies) * 0.99)],
            'min': min(latencies),
            'max': max(latencies)
        }

    login_stats = calc_latency_stats(stats.login_latencies)
    register_stats = calc_latency_stats(stats.register_latencies)
    match_stats = calc_latency_stats(stats.match_latencies)
    move_stats = calc_latency_stats(stats.move_latencies)

    success_rate = (stats.successful_logins * 100.0 / TOTAL_USERS) if TOTAL_USERS > 0 else 0

    perf_stats = perf_monitor.get_stats()

    logger.info("=" * 40)
    logger.info("  压力测试完成 - 最终统计")
    logger.info("=" * 40)
    logger.info(f"  总用户数: {TOTAL_USERS}")
    logger.info(f"  在线用户: {stats.online_users} ({stats.online_users * 100.0 / TOTAL_USERS:.1f}%)")
    logger.info(f"  登录成功: {stats.successful_logins} ({success_rate:.1f}%)")
    logger.info(f"  登录失败: {stats.failed_logins}")
    if stats.successful_registers > 0 or stats.failed_registers > 0:
        logger.info(f"  注册成功: {stats.successful_registers}, 失败: {stats.failed_registers}")
    logger.info(f"  已完成对局: {stats.completed_games}")
    logger.info(f"  总发送消息: {stats.total_messages_sent}")
    logger.info(f"  总接收消息: {stats.total_messages_received}")
    logger.info("")
    logger.info("  延迟统计:")
    logger.info(f"  登录延迟 - 平均: {login_stats['avg']:.2f}ms, "
                f"P50: {login_stats['p50']:.2f}ms, "
                f"P95: {login_stats['p95']:.2f}ms, "
                f"P99: {login_stats['p99']:.2f}ms")
    if stats.register_latencies:
        logger.info(f"  注册延迟 - 平均: {register_stats['avg']:.2f}ms, "
                    f"P50: {register_stats['p50']:.2f}ms, "
                    f"P95: {register_stats['p95']:.2f}ms")
    if stats.match_latencies:
        logger.info(f"  匹配延迟 - 平均: {match_stats['avg']:.2f}ms, "
                    f"P50: {match_stats['p50']:.2f}ms, "
                    f"P95: {match_stats['p95']:.2f}ms")
    if stats.move_latencies:
        logger.info(f"  落子延迟 - 平均: {move_stats['avg']:.2f}ms, "
                    f"P50: {move_stats['p50']:.2f}ms, "
                    f"P95: {move_stats['p95']:.2f}ms")
    logger.info("")
    logger.info(f"  消息发送速率: {stats.total_messages_sent / TEST_DURATION:.2f} msg/s")
    logger.info(f"  消息接收速率: {stats.total_messages_received / TEST_DURATION:.2f} msg/s")
    if PSUTIL_AVAILABLE:
        logger.info("")
        logger.info("  性能监控:")
        logger.info(f"  CPU使用 - 平均: {perf_stats['cpu_avg']:.1f}%, 最高: {perf_stats['cpu_max']:.1f}%")
        logger.info(f"  内存使用 - 平均: {perf_stats['memory_avg']:.1f}MB, "
                    f"最高: {perf_stats['memory_max']:.1f}MB")
    if stats.errors:
        logger.info("")
        logger.info("  错误统计:")
        for error_type, count in stats.errors.items():
            logger.info(f"  {error_type}: {count}")
    logger.info("=" * 40)


def main():
    """主函数"""
    try:
        asyncio.run(run_load_test())
    except KeyboardInterrupt:
        logger.info("测试被用户中断")
        perf_monitor.stop()  # 停止性能监控
        print_final_stats()
    except Exception as e:
        logger.error(f"压力测试失败: {e}")
        perf_monitor.stop()  # 确保停止性能监控
        raise


if __name__ == "__main__":
    main()
