<template>
  <div class="not-found">
    <div class="not-found-card fade-in-up">
      <div class="not-found-code">404</div>
      <h1 class="not-found-title">页面不存在</h1>
      <p class="not-found-desc">你访问的页面已经消失在云端</p>
      <el-button type="primary" size="large" @click="router.push({ name: 'Files' })">
        <el-icon><HomeFilled /></el-icon>&nbsp;返回首页
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { HomeFilled } from '@element-plus/icons-vue'

const router = useRouter()
</script>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--cb-bg-page);
  padding: 24px;
}

.not-found-card {
  text-align: center;
  background: var(--cb-bg-card);
  border-radius: var(--cb-radius-xl);
  padding: 64px 80px;
  box-shadow: var(--cb-shadow-lg);
}

.not-found-code {
  font-size: 80px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(135deg, var(--cb-primary), var(--cb-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 16px;
}

.not-found-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--cb-text-primary);
  margin-bottom: 12px;
}

.not-found-desc {
  font-size: 14px;
  color: var(--cb-text-muted);
  margin-bottom: 24px;
}
</style>
