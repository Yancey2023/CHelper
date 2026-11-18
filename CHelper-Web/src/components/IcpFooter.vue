<script setup lang="ts">
const icpText = import.meta.env.VITE_ICP_TEXT as string | undefined
</script>

<template>
  <div v-if="icpText" class="text-center text-xs text-muted-foreground">
    <a class="underline-offset-4 hover:underline" href="https://beian.miit.gov.cn/shouye.html">
      {{ icpText }}
    </a>
  </div>
</template>
