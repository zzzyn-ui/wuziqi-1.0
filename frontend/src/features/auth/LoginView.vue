<template>
  <div class="login-container">
    <canvas id="particleCanvas" class="particle-canvas"></canvas>

    <div class="side-decoration left">
      <div class="decoration-item"></div>
      <div class="decoration-item"></div>
      <div class="decoration-item"></div>
      <div class="decoration-item"></div>
      <div class="decoration-line"></div>
    </div>

    <div class="side-decoration right">
      <div class="decoration-item"></div>
      <div class="decoration-item"></div>
      <div class="decoration-item"></div>
      <div class="decoration-item"></div>
      <div class="decoration-line"></div>
    </div>

    <div class="login-card">
      <div class="login-header">
        <h1 class="login-title">五子棋在线对战</h1>
        <p class="login-subtitle">欢迎回来，请登录您的账户</p>
      </div>

      <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" class="login-form" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" clearable :prefix-icon="User" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="请输入密码" size="large" show-password
            :prefix-icon="Lock" @keyup.enter="handleLogin" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-footer">
        <span class="footer-text">还没有账户？</span>
        <router-link to="/register" class="register-link">立即注册</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { User, Lock } from "@element-plus/icons-vue";
import { useUserStore } from "@/store/modules/user";

const router = useRouter();
const userStore = useUserStore();

const loginFormRef = ref<FormInstance>();
const loading = ref(false);

const loginForm = reactive({
  username: "",
  password: "",
});

const loginRules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度在 3 到 20 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度在 6 到 20 个字符", trigger: "blur" },
  ],
};

const handleLogin = async () => {
  if (!loginFormRef.value) return;

  try {
    await loginFormRef.value.validate();
    loading.value = true;

    const success = await userStore.login({
      username: loginForm.username,
      password: loginForm.password,
    });

    if (success) {
      const redirect = router.currentRoute.value.query.redirect as string;
      router.push(redirect || "/home");
    } else {
      ElMessage.error("登录失败，请检查用户名和密码");
    }
  } finally {
    loading.value = false;
  }
};

let animationFrameId: number | null = null;

class Particle {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  x: number;
  y: number;
  size: number;
  speedX: number;
  speedY: number;
  opacity: number;
  color: string;

  constructor(canvas: HTMLCanvasElement, ctx: CanvasRenderingContext2D) {
    this.canvas = canvas;
    this.ctx = ctx;
    this.reset();
  }

  reset() {
    this.x = Math.random() * this.canvas.width;
    this.y = Math.random() * this.canvas.height;
    this.size = Math.random() * 8 + 3;
    this.speedX = (Math.random() - 0.5) * 0.5;
    this.speedY = (Math.random() - 0.5) * 0.5 - 0.3;
    this.opacity = Math.random() * 0.5 + 0.2;
    this.color = `rgba(255, ${Math.floor(Math.random() * 100 + 100)}, ${Math.floor(Math.random() * 50 + 50)}, ${this.opacity})`;
  }

  update() {
    this.x += this.speedX;
    this.y += this.speedY;
    this.opacity -= 0.002;

    if (this.opacity <= 0 || this.y < -20 || this.y > this.canvas.height + 20) {
      this.reset();
      this.y = this.canvas.height + 20;
      this.opacity = Math.random() * 0.5 + 0.2;
    }
  }

  draw() {
    this.ctx.beginPath();
    this.ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
    this.ctx.fillStyle = this.color.replace(/[^,]+(?=\))/, this.opacity.toString());
    this.ctx.fill();
  }
};

const initParticles = () => {
  const canvas = document.getElementById("particleCanvas") as HTMLCanvasElement;
  if (!canvas) return;

  const ctx = canvas.getContext("2d");
  if (!ctx) return;

  const resizeCanvas = () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  };

  const particles: Particle[] = [];

  const initParticleArray = () => {
    particles.length = 0;
    const particleCount = Math.floor((canvas.width * canvas.height) / 15000);
    for (let i = 0; i < particleCount; i++) {
      particles.push(new Particle(canvas, ctx));
    }
  };

  const animateParticles = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    particles.forEach((particle) => {
      particle.update();
      particle.draw();
    });
    animationFrameId = requestAnimationFrame(animateParticles);
  };

  resizeCanvas();
  initParticleArray();
  animateParticles();

  window.addEventListener("resize", () => {
    resizeCanvas();
    initParticleArray();
  });
};

onMounted(() => {
  initParticles();
});

onUnmounted(() => {
  if (animationFrameId !== null) {
    cancelAnimationFrame(animationFrameId);
  }
});
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fff5e6 0%, #ffe8cc 50%, #ffd9a8 100%);
  padding: 20px;
  position: relative;
  overflow: hidden;
}

.particle-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
}

.side-decoration {
  position: fixed;
  top: 50%;
  transform: translateY(-50%);
  width: 150px;
  height: 400px;
  z-index: 1;
  pointer-events: none;
}

.side-decoration.left {
  left: 5%;
}

.side-decoration.right {
  right: 5%;
}

.decoration-item {
  position: absolute;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 149, 0, 0.3), rgba(255, 179, 71, 0.1));
  animation: float 4s ease-in-out infinite;
}

.decoration-item:nth-child(1) {
  top: 10%;
  left: 20%;
  animation-delay: 0s;
}

.decoration-item:nth-child(2) {
  top: 30%;
  right: 20%;
  animation-delay: 0.5s;
}

.decoration-item:nth-child(3) {
  top: 50%;
  left: 10%;
  animation-delay: 1s;
}

.decoration-item:nth-child(4) {
  top: 70%;
  right: 10%;
  animation-delay: 1.5s;
}

@keyframes float {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-20px) scale(1.1);
  }
}

.decoration-line {
  position: absolute;
  width: 2px;
  height: 100%;
  background: linear-gradient(to bottom, transparent, rgba(255, 149, 0, 0.2), transparent);
  left: 50%;
  top: 0;
}

.login-card {
  position: relative;
  z-index: 2;
  background: white;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 48px 40px;
  width: 100%;
  max-width: 420px;
}

.login-header {
  text-align: center;
  margin-bottom: 40px;
}

.login-title {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.login-subtitle {
  font-size: 14px;
  color: #666;
  margin: 0;
}

.login-form {
  margin-bottom: 24px;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.login-form :deep(.el-input__wrapper) {
  border-radius: 8px;
  padding: 8px 16px;
}

.login-button {
  width: 100%;
  border-radius: 8px;
  height: 44px;
  font-size: 16px;
  font-weight: 500;
  background: linear-gradient(135deg, #ff9a8b 0%, #ff6a88 100%);
  border: none;
  transition: all 0.3s ease;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255, 106, 136, 0.4);
}

.login-button:active {
  transform: translateY(0);
}

.login-footer {
  text-align: center;
  font-size: 14px;
  color: #666;
}

.footer-text {
  margin-right: 8px;
}

.register-link {
  color: #ff6a88;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s ease;
}

.register-link:hover {
  color: #ff9a8b;
}

@media (max-width: 480px) {
  .login-card {
    padding: 32px 24px;
  }

  .login-title {
    font-size: 24px;
  }
}
</style>
