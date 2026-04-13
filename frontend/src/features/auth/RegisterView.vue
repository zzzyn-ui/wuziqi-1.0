<template>
  <div class="register-view">
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

    <div class="register-card">
      <h1>注册账号</h1>
      <p class="subtitle">加入五子棋对战平台</p>

      <el-form :model="registerForm" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="registerForm.username" placeholder="用户名" size="large" prefix-icon="User" />
        </el-form-item>

        <el-form-item prop="password">
          <el-input v-model="registerForm.password" type="password" placeholder="密码" size="large" prefix-icon="Lock"
            show-password />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" type="password" placeholder="确认密码" size="large"
            prefix-icon="Lock" show-password />
        </el-form-item>

        <el-form-item prop="nickname">
          <el-input v-model="registerForm.nickname" placeholder="昵称（可选）" size="large" prefix-icon="UserFilled" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="handleRegister" class="register-button">
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="footer">
        <span>已有账号？</span>
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, type FormInstance, type FormRules } from "element-plus";
import { useUserStore } from "@/store/modules/user";

const router = useRouter();
const userStore = useUserStore();
const formRef = ref<FormInstance>();
const loading = ref(false);

const registerForm = reactive({
  username: "",
  password: "",
  confirmPassword: "",
  nickname: "",
});

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value === "") {
    callback(new Error("请再次输入密码"));
  } else if (value !== registerForm.password) {
    callback(new Error("两次输入密码不一致"));
  } else {
    callback();
  }
};

const rules: FormRules = {
  username: [
    { required: true, message: "请输入用户名", trigger: "blur" },
    { min: 3, max: 20, message: "用户名长度为 3 到 20 个字符", trigger: "blur" },
  ],
  password: [
    { required: true, message: "请输入密码", trigger: "blur" },
    { min: 6, max: 20, message: "密码长度为 6 到 20 个字符", trigger: "blur" },
  ],
  confirmPassword: [{ required: true, validator: validateConfirmPassword, trigger: "blur" }],
};

const handleRegister = async () => {
  if (!formRef.value) return;

  await formRef.value.validate(async (valid) => {
    if (!valid) return;

    loading.value = true;
    try {
      await userStore.register({
        username: registerForm.username,
        password: registerForm.password,
        confirmPassword: registerForm.confirmPassword,
        nickname: registerForm.nickname || undefined,
      });
      await router.push("/login");
    } catch (error: any) {
      ElMessage.error(error.message || "注册失败");
    } finally {
      loading.value = false;
    }
  });
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
.register-view {
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

.register-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 400px;
  padding: 40px;
  background: white;
  border-radius: 20px;
  box-shadow: 0 10px 40px rgba(255, 107, 53, 0.2);
}

.register-card h1 {
  font-size: 28px;
  color: #ff6b35;
  text-align: center;
  margin-bottom: 5px;
}

.subtitle {
  text-align: center;
  color: #999;
  margin-bottom: 30px;
  font-size: 14px;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-input__wrapper) {
  border-radius: 10px;
}

.register-button {
  width: 100%;
  background: linear-gradient(135deg, #ff8c61 0%, #ff6b35 100%);
  border: none;
  height: 45px;
  font-size: 16px;
}

.footer {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #666;
}

.footer a {
  color: #ff6b35;
  text-decoration: none;
  font-weight: 500;
}

.footer a:hover {
  text-decoration: underline;
}
</style>
