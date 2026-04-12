<script setup>
import {store} from '../store';
</script>

<template>
  <Transition name="fade">
    <div v-if="store.isLoading"
         class="fixed inset-0 z-[300] flex flex-col items-center justify-center bg-black/60 backdrop-blur-2xl">

      <!-- Animated Logo Container -->
      <div class="relative w-32 h-32 flex items-center justify-center">
        <div
            class="relative z-10 flex h-24 w-24 items-center justify-center rounded-[28px] border border-white/10 bg-white/5 shadow-2xl shadow-cyan-500/10 backdrop-blur-xl">
          <svg class="h-14 w-auto logo-heartbeat text-white fill-current" viewBox="0 0 100 100">
            <rect height="50" rx="6" width="80" x="10" y="25"/>
            <path d="M25 35 V65 H38 V35 L55 65 V35 H68 V65" fill="none" stroke="black" stroke-linecap="square"
                  stroke-width="8"/>
          </svg>
        </div>
      </div>

      <!-- Loading Text -->
      <div class="mt-8 flex flex-col items-center gap-2">
        <p class="text-white/60 text-sm font-medium tracking-[0.2em] uppercase">Initializing Industrial Engine</p>
        <div class="flex gap-1.5 mt-2">
          <div class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-bounce [animation-delay:-0.3s]"></div>
          <div class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-bounce [animation-delay:-0.15s]"></div>
          <div class="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-bounce"></div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.animate-spin-slow {
  animation: spin 6s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
