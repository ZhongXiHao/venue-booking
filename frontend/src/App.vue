<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-inner">
        <h1>场地预约系统</h1>

        <nav class="navigation">
          <RouterLink to="/venues">
            场地列表
          </RouterLink>

          <RouterLink to="/reservations">
            预约记录
          </RouterLink>

          <RouterLink to="/reservations/new">
            创建预约
          </RouterLink>

          <RouterLink to="/venues/new">
            创建场地
          </RouterLink>
        </nav>
      </div>
    </header>

    <main class="page-container">
      <RouterView />
    </main>
  </div>
</template>