<template>
  <div class="page-header-shared">
    <div class="header-background">
      <canvas v-if="showParticles" ref="particleCanvas" class="particle-canvas"></canvas>
    </div>
    <div class="header-content">
      <div class="header-left">
        <button v-if="showBack" class="back-btn" @click="$router.back()">
          <span>←</span>
        </button>
        <div class="header-text">
          <h1>{{ title }}</h1>
          <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        </div>
      </div>
      <div v-if="$slots.actions" class="header-actions">
        <slot name="actions"></slot>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

interface Props {
  title: string
  subtitle?: string
  showBack?: boolean
  showParticles?: boolean
  gradientFrom?: string
  gradientTo?: string
}

const props = withDefaults(defineProps<Props>(), {
  showBack: false,
  showParticles: true,
  gradientFrom: '#ff6b35',
  gradientTo: '#ff8c61'
})

const particleCanvas = ref<HTMLCanvasElement | null>(null)

// 粒子动画
let animationFrame: number | null = null

onMounted(() => {
  if (props.showParticles && particleCanvas.value) {
    initParticles()
  }
})

onUnmounted(() => {
  if (animationFrame) {
    cancelAnimationFrame(animationFrame)
  }
})

const initParticles = () => {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const resizeCanvas = () => {
    canvas.width = canvas.offsetWidth
    canvas.height = canvas.offsetHeight
  }

  const particles: Array<{
    x: number
    y: number
    size: number
    speedX: number
    speedY: number
    opacity: number
  }> = []

  const createParticles = () => {
    particles.length = 0
    const count = Math.floor((canvas.width * canvas.height) / 15000)
    for (let i = 0; i < count; i++) {
      particles.push({
        x: Math.random() * canvas.width,
        y: Math.random() * canvas.height,
        size: Math.random() * 8 + 3,
        speedX: (Math.random() - 0.5) * 0.5,
        speedY: (Math.random() - 0.5) * 0.5 - 0.3,
        opacity: Math.random() * 0.5 + 0.2
      })
    }
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    particles.forEach(p => {
      p.x += p.speedX
      p.y += p.speedY
      p.opacity -= 0.002

      if (p.opacity <= 0 || p.y < -20 || p.y > canvas.height + 20) {
        p.y = canvas.height + 20
        p.opacity = Math.random() * 0.5 + 0.2
      }

      ctx.beginPath()
      ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(255, 255, 255, ${p.opacity})`
      ctx.fill()
    })

    animationFrame = requestAnimationFrame(animate)
  }

  resizeCanvas()
  createParticles()
  animate()

  window.addEventListener('resize', () => {
    resizeCanvas()
    createParticles()
  })
}
</script>

<style scoped>
.page-header-shared {
  position: relative;
  padding: 40px 20px 30px;
  text-align: center;
  overflow: hidden;
}

.header-background {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(135deg, v-bind(gradientFrom) 0%, v-bind(gradientTo) 100%);
  z-index: 0;
}

.header-background::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: linear-gradient(to bottom, transparent 0%, rgba(0, 0, 0, 0.1) 100%);
  pointer-events: none;
}

.particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.header-content {
  position: relative;
  z-index: 1;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.back-btn {
  background: rgba(255, 255, 255, 0.2);
  border: none;
  color: white;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.3);
  transform: scale(1.05);
}

.header-text h1 {
  font-size: 32px;
  color: white;
  margin-bottom: 5px;
}

.subtitle {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.9);
}

.header-actions {
  position: absolute;
  right: 20px;
}

@media (max-width: 768px) {
  .page-header-shared {
    padding: 30px 20px 20px;
  }

  .header-text h1 {
    font-size: 24px;
  }

  .header-actions {
    position: static;
    margin-left: auto;
  }
}
</style>
