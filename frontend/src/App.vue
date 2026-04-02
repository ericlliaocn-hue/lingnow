<template>
  <router-view/>
</template>

<script setup>
// Main App Entry
</script>

<style>
/* FORCE UNLOCK SCROLL */
html, body, #app {
  height: auto !important;
  min-height: 100vh !important;
  overflow-y: visible !important;
  overflow-x: hidden !important;
}

#app {
  width: 100%;
}
</style>
