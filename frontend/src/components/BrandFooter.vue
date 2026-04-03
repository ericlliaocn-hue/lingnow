<template>
  <footer class="py-32 px-8 border-t border-white/5 bg-black/20">
    <div class="max-w-7xl mx-auto grid grid-cols-1 md:grid-cols-4 gap-12 text-left">
      <div class="col-span-2">
        <div class="flex items-center gap-4 mb-6">
          <!-- Pure Proposal U SVG (No extra wrapper) -->
          <svg class="h-10 w-auto text-white fill-current" viewBox="0 0 100 100">
            <rect height="50" rx="6" width="80" x="10" y="25"/>
            <path d="M25 35 V65 H38 V35 L55 65 V35 H68 V65" fill="none" stroke="black" stroke-linecap="square"
                  stroke-width="8"/>
          </svg>
          <span class="text-2xl font-black tracking-tighter uppercase italic text-white">LingNow</span>
        </div>
        <p class="text-gray-500 text-sm max-w-xs leading-relaxed">
          {{
            isCn ? '面向自主代码生成与高保真原型设计的工业级 AI 软件工厂。' : 'The industrial AI factory for autonomous code generation and high-fidelity prototypes.'
          }}
        </p>
      </div>

      <div>
        <h4 class="text-[10px] font-bold text-gray-400 uppercase tracking-[0.3em] mb-6">{{
            isCn ? '产品' : 'Product'
          }}</h4>
        <ul class="space-y-4 text-xs text-gray-500 font-bold uppercase tracking-widest">
          <li>
            <router-link class="hover:text-white transition-colors" to="/solutions">{{
                isCn ? '解决方案' : 'Solutions'
              }}
            </router-link>
          </li>
          <li>
            <router-link class="hover:text-white transition-colors" to="/workbench">{{
                isCn ? '工作台' : 'Workbench'
              }}
            </router-link>
          </li>
          <li>
            <router-link class="hover:text-white transition-colors" to="/updates">{{
                isCn ? '动态' : 'Updates'
              }}
            </router-link>
          </li>
        </ul>
      </div>

      <div>
        <h4 class="text-[10px] font-bold text-gray-400 uppercase tracking-[0.3em] mb-6">{{
            isCn ? '公司' : 'Company'
          }}</h4>
        <ul class="space-y-4 text-xs text-gray-500 font-bold uppercase tracking-widest">
          <li>
            <router-link class="hover:text-white transition-colors" to="/about">{{
                isCn ? '关于我们' : 'About'
              }}
            </router-link>
          </li>
          <li>
            <router-link class="hover:text-white transition-colors" to="/docs">{{
                isCn ? '文档' : 'Docs'
              }}
            </router-link>
          </li>
          <li><a class="hover:text-white transition-colors" href="#">{{ isCn ? '隐私政策' : 'Privacy' }}</a></li>
        </ul>
      </div>
    </div>

    <div
        class="max-w-7xl mx-auto mt-20 pt-8 border-t border-white/5 text-[9px] text-gray-700 uppercase tracking-[0.5em] font-bold">
      LINGNOW FACTORY_MATRIX © 2026. AUTH_SIG_B0X1
    </div>
  </footer>
</template>

<script setup>
import {computed} from 'vue'

const isCn = computed(() => (localStorage.getItem('lang') || 'cn') === 'cn')
</script>
