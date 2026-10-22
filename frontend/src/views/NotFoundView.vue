<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section>
    <h2>404：页面不存在</h2>
    <p>请检查访问地址，或返回场地列表继续操作。</p>

    <RouterLink to="/venues">
      返回场地列表
    </RouterLink>
  </section>
</template>