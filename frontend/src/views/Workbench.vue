<script setup>
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import axios from 'axios'
import {
  ArrowUpRight,
  Box,
  Globe,
  Hand,
  History,
  Image,
  Layers,
  Layout,
  Loader2,
  Monitor,
  MousePointer2,
  Palette,
  PanelRight,
  Play,
  Pointer,
  Send,
  Share2,
  Smartphone,
  Sparkles,
  SquareDashedMousePointer,
  Star,
  Tablet,
  Terminal,
  X
} from 'lucide-vue-next'
import {Sandpack} from 'sandpack-vue3'

const route = useRoute()
const router = useRouter()

const prompt = ref('')
const loading = ref(false)
const result = ref(null)
const error = ref(null)

const locale = ref(localStorage.getItem('lingnow_lang') || 'ZH')
const history = ref([])
const isHistoryOpen = ref(false)
const AUTH_REQUIRED_MESSAGE = '登录已失效，请重新登录'

const i18n = computed(() => ({
  ZH: {
    welcome: '你想建造什么？',
    subtitle: '告诉数字军团你的创意，我们将实时为您构建全栈应用。',
    placeholder: '在这里描述您的增量需求或新愿景...',
    tab_canvas: '视觉样机',
    tab_logic: '排期逻辑',
    tab_source: '源码视图',
    tab_sandbox: '在线沙盒',
    history_title: '项目档案管理',
    history_empty: '暂无存档记录',
    new_project: '新建项目',
    PLANNING: '需求分析',
    DESIGNING: '视觉对标',
    CODING: '代码合成',
    DONE: '资产交付',
    history: '项目档案',
    switch_lang: 'ZH/EN',
    action: '生成原型',
    loading_subtitle: '正在调度全球算力，请稍候...'
  },
  EN: {
    welcome: 'What do you want to build?',
    subtitle: 'Build full-stack apps in real-time.',
    placeholder: 'Describe your vision here...',
    tab_canvas: 'Canvas',
    tab_logic: 'Logic',
    tab_source: 'Source',
    tab_sandbox: 'Sandbox',
    history_title: 'Project Archive',
    history_empty: 'No archives yet',
    new_project: 'New Project',
    PLANNING: 'Planning',
    DESIGNING: 'Designing',
    CODING: 'Coding',
    DONE: 'Done',
    history: 'Archives',
    switch_lang: 'EN/ZH',
    action: 'Generate',
    loading_subtitle: 'Orchestrating computing power...'
  }
}[locale.value]))

const toggleLang = () => {
  locale.value = locale.value === 'ZH' ? 'EN' : 'ZH'
  localStorage.setItem('lingnow_lang', locale.value)
}

const fetchHistory = async () => {
  if (!ensureAuthenticated()) return
  try {
    const res = await axios.get('/api/projects/all')
    history.value = res.data
  } catch (err) {
    if (isAuthError(err)) return
    console.error('Failed to fetch history', err)
  }
}

const loadProject = async (id) => {
  if (!ensureAuthenticated()) return
  if (!id) return
  if (result.value && result.value.id === id) return // Already loaded

  console.log('Loading project from API:', id)
  loading.value = true
  error.value = null
  try {
    const res = await axios.get(`/api/projects/${id}`)
    const normalizedProject = normalizeWorkbenchResult(res.data)
    result.value = normalizedProject
    currentSessionId.value = id
    isHistoryOpen.value = false
    isDesignStarted.value = !!normalizedProject?.prototypeHtml
    activeTab.value = resolveActiveTab(normalizedProject)
    if (route.params.id !== id) {
      router.push(`/project/${id}`)
    }
  } catch (err) {
    if (isAuthError(err)) return
    console.error('Failed to load project', err)
    error.value = '加载项目失败'
    result.value = null
  } finally {
    loading.value = false
  }
}

const isWorkbenchMode = computed(() => !!result.value && !error.value)
const isSidebarOpen = ref(true)
const deviceType = ref('desktop')
const isMirrorMode = ref(false)
const mirrorDevices = ref(['desktop', 'mobile'])
const isDebugMode = ref(false)

const sandpackFiles = computed(() => {
  if (!result.value || !result.value.files) return {}
  const files = {}
  Object.entries(result.value.files).forEach(([path, content]) => {
    const cleanPath = path.startsWith('/') ? path.substring(1) : path
    files[cleanPath] = content
  })
  return files
})

const experienceBrief = computed(() => result.value?.prototypeBundle?.experienceBrief || null)
const experienceScreens = computed(() => experienceBrief.value?.screens || [])
const experienceTraits = computed(() => experienceBrief.value?.inferredTraits || [])
const projectTitle = computed(() => result.value?.overview || result.value?.title || (result.value?.userIntent ? '当前项目' : '新建项目'))
const projectModeLabel = computed(() => result.value?.id ? 'Active Project' : 'Idea Workspace')
const visibleFeatures = computed(() => (result.value?.features || []).slice(0, 6))
const loopPreview = computed(() => (experienceBrief.value?.primaryLoopSteps || []).slice(0, 4))
const showPlanCta = computed(() => !isDesignStarted.value && result.value?.status === 'PLANNING')
const latestSnapshots = computed(() => [...snapshots.value].reverse().slice(0, 4))
const recentLogs = computed(() => runtimeLogs.value.slice(-4).reverse())
const workbenchPlaceholder = computed(() => locale.value === 'ZH'
    ? '描述产品目标、参考对象、核心页面或关键流程...'
    : 'Describe the product goal, references, key screens, or core flow...')
const projectLabel = computed(() => {
  const source = (
      experienceBrief.value?.referenceSignal
      || result.value?.overview
      || result.value?.title
      || result.value?.userIntent?.split(/[，。,.]/)[0]
      || prompt.value.split(/[，。,.]/)[0]
      || ''
  ).trim()
  if (!source) return '新建项目'
  return source.length > 12 ? `${source.slice(0, 12)}…` : source
})
const showCanvasControls = computed(() => isWorkbenchMode.value && (activeTab.value === 'design' || activeTab.value === 'preview'))
const commandActionLabel = computed(() => {
  if (showPlanCta.value) return '确认理解'
  if (result.value?.id) return '继续生成'
  return i18n.value.action
})
const logExpanded = ref(false)
const insightDrawerOpen = ref(true)
const insightContentVisible = ref(true)
const operationTrail = ref([])
const seedPlanTriggered = ref(false)
let insightDrawerTimer = null
const hasInsightPayload = computed(() => activeTab.value === 'plan')
const hasResolvedInsight = computed(() => !!experienceBrief.value)
const insightHeaderLabel = computed(() => experienceBrief.value?.referenceSignal || projectLabel.value)
const compactScreenBullet = (title) => {
  const text = String(title || '').trim()
  if (!text) return {label: '', desc: ''}
  const parts = text.split(/[，,:：]/)
  const label = parts[0]?.trim() || text
  const desc = text.slice(label.length).replace(/^[，,:：\s]+/, '').trim()
  return {label, desc}
}
const insightScreenBullets = computed(() => {
  const bullets = experienceBrief.value?.screenBullets
  if (bullets?.length) {
    return bullets.map((bullet) => ({
      ...bullet,
      desc: bullet.description || ''
    }))
  }
  return []
})
const insightIntro = computed(() => experienceBrief.value?.introduction || '')
const insightScreenPlanTitle = computed(() => experienceBrief.value?.screenPlanTitle || '')
const insightFooterText = computed(() => experienceBrief.value?.nextStepNarrative || '')
const logTrailItems = computed(() => {
  if (operationTrail.value.length) return operationTrail.value.slice(-3)
  if (recentLogs.value.length) return recentLogs.value.slice(-3).map((item) => item.msg)
  return []
})

const user = ref(JSON.parse(localStorage.getItem('user') || 'null'))
const isAuthenticated = computed(() => !!user.value?.token)

const enterLoginState = (message = AUTH_REQUIRED_MESSAGE) => {
  isHistoryOpen.value = false
  isDesignStarted.value = false
  result.value = null
  history.value = []
  error.value = message
  user.value = null
  localStorage.removeItem('user')
  const redirect = route.fullPath && route.fullPath !== '/login' ? route.fullPath : '/'
  if (route.path !== '/login') {
    router.replace({name: 'Login', query: {redirect}})
  }
}

const ensureAuthenticated = (message = AUTH_REQUIRED_MESSAGE) => {
  if (isAuthenticated.value) return true
  enterLoginState(message)
  return false
}

const isAuthError = (err) => {
  const status = err?.response?.status
  const message = String(err?.response?.data?.error || err?.response?.data?.message || err?.message || '')
  return status === 401
      || status === 403
      || message.includes('未能读取到有效 token')
      || message.toLowerCase().includes('token')
}

const formatVersionLabel = (version) => String(version || '0.0.1').replace(/^v/i, '')

const normalizeWorkbenchResult = (payload) => {
  if (!payload) return null

  const manifest = payload.manifest ? payload.manifest : payload
  const files = payload.files ?? manifest.files ?? manifest.generatedFiles ?? {}
  const dependencies = payload.dependencies ?? manifest.dependencies ?? {}

  return {
    ...manifest,
    title: payload.title ?? manifest.title,
    description: payload.description ?? manifest.description,
    files,
    generatedFiles: manifest.generatedFiles ?? files,
    dependencies,
    snapshots: manifest.snapshots ?? [],
    version: manifest.version ?? '0.0.1'
  }
}

const hasGeneratedFiles = (project) => Object.keys(project?.files || {}).length > 0

const resolveActiveTab = (project) => {
  if (!project) return 'plan'
  if (hasGeneratedFiles(project)) return 'preview'
  if (project.prototypeHtml) return 'design'
  return 'plan'
}

// Inspector & Versioning State
// Inspector & Versioning State
const activeTab = ref('plan')
const selectedNode = ref(null)
const isDesignStarted = ref(false)
const snapshots = computed(() => result.value?.snapshots || [])
const currentVersion = computed(() => formatVersionLabel(result.value?.version))
const canStartCoding = computed(() =>
    result.value?.metaData?.design_ready === 'true' &&
    !!result.value?.prototypeHtml &&
    !hasGeneratedFiles(result.value)
)
const isDesignRefining = computed(() => {
  const project = result.value
  if (!project?.prototypeHtml) return false
  return project?.metaData?.design_ready !== 'true' && String(project?.status || '') === 'DESIGNING'
})
const designStatusText = computed(() => {
  if (!result.value?.prototypeHtml) return ''
  if (result.value?.metaData?.design_ready === 'true') return locale.value === 'ZH' ? '精修完成' : 'Polished'
  if (String(result.value?.status || '') === 'QA') {
    return locale.value === 'ZH' ? '审计未通过，显示可编辑草稿' : 'Audit needs fixes, editable draft shown'
  }
  return locale.value === 'ZH' ? '种子稿已显示，AI 正在后台精修' : 'Seed draft shown, AI is polishing in the background'
})
let projectRefreshTimer = null

const formatDate = (timestamp) => {
  if (!timestamp) return '未知'
  const date = new Date(timestamp)
  return date.toLocaleString(locale.value === 'ZH' ? 'zh-CN' : 'en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const pushOperationTrail = (label) => {
  const text = String(label || '').trim()
  if (!text) return
  if (operationTrail.value[operationTrail.value.length - 1] === text) return
  operationTrail.value = [...operationTrail.value, text].slice(-10)
}

const loadSeedTrail = () => {
  try {
    const raw = sessionStorage.getItem('lingnow_seed_trail')
    if (!raw || operationTrail.value.length) return
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed) && parsed.length) {
      operationTrail.value = parsed.map((item) => String(item || '').trim()).filter(Boolean).slice(-10)
      if (operationTrail.value.length) {
        logExpanded.value = true
      }
    }
  } catch (e) {
    // ignore malformed seed trail
  }
}

const toggleInsightDrawer = () => {
  if (insightDrawerTimer) {
    clearTimeout(insightDrawerTimer)
    insightDrawerTimer = null
  }

  if (insightDrawerOpen.value) {
    insightContentVisible.value = false
    insightDrawerTimer = setTimeout(() => {
      insightDrawerOpen.value = false
      insightDrawerTimer = null
    }, 140)
    return
  }

  insightDrawerOpen.value = true
  insightDrawerTimer = setTimeout(() => {
    insightContentVisible.value = true
    insightDrawerTimer = null
  }, 220)
}

const maybeTriggerSeedPlan = async () => {
  const seedPrompt = typeof route.query.p === 'string' ? route.query.p.trim() : ''
  if (!seedPrompt || seedPlanTriggered.value || result.value || route.params.id || loading.value) {
    return
  }
  seedPlanTriggered.value = true
  prompt.value = seedPrompt
  await nextTick()
  await handleGenerate()
}

// Hybrid Map State
const renderFailed = ref(false)
const treeData = ref(null)
const lastParsedId = ref(null)

const parseMindMap = (text) => {
  if (!text) return null
  const lines = text.split('\n').filter(l => l.trim().length > 0)
  const root = {
    id: 'root-' + Math.random().toString(36).substr(2, 9),
    name: '',
    children: [],
    isVirtualRoot: true
  }
  const stack = [{node: root, indent: -1}]

  lines.forEach(line => {
    const indent = line.search(/\S/)
    const name = line.trim()
    const node = {id: 'node-' + Math.random().toString(36).substr(2, 9), name, children: []}

    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop()
    }

    if (stack.length > 0) {
      stack[stack.length - 1].node.children.push(node)
      stack.push({node, indent})
    }
  })

  return root
}

const serializeTree = (node, indent = 0) => {
  if (!node) return ''
  let text = ''
  if (!node.isVirtualRoot) {
    text = ' '.repeat(indent) + node.name + '\n'
  }
  if (node.children) {
    node.children.forEach(child => {
      text += serializeTree(child, node.isVirtualRoot ? indent : indent + 2)
    })
  }
  return text
}

const syncTreeToMindMap = () => {
  if (treeData.value && result.value) {
    result.value.mindMap = serializeTree(treeData.value)
  }
}

const addChild = (parent) => {
  if (!parent.children) parent.children = []
  parent.children.push({name: '新功能节点', children: []})
  syncTreeToMindMap()
}

const removeChild = (parent, index) => {
  parent.children.splice(index, 1)
  syncTreeToMindMap()
}

const requestInterceptorId = axios.interceptors.request.use(config => {
  const url = String(config.url || '')
  const isAuthRequest = url.includes('/api/auth/login') || url.includes('/api/auth/register')
  if (!isAuthRequest && !isAuthenticated.value) {
    enterLoginState()
    return Promise.reject(new Error('AUTH_REQUIRED'))
  }
  if (isAuthenticated.value) {
    config.headers['satoken'] = user.value.token
  }
  return config
}, error => Promise.reject(error))

const responseInterceptorId = axios.interceptors.response.use(
    response => response,
    error => {
      if (isAuthError(error)) {
        enterLoginState()
      }
      return Promise.reject(error)
    }
)

const handleLogout = () => {
  user.value = null
  localStorage.removeItem('user')
  isHistoryOpen.value = false
  result.value = null
  router.push('/')
}

watch(isDebugMode, (newVal) => {
  const iframes = document.querySelectorAll('iframe')
  iframes.forEach(iframe => {
    iframe.contentWindow?.postMessage({type: 'lingnow-debug', enabled: newVal}, '*')
  })
})

const inspectedId = ref(null)
const runtimeLogs = ref([])

onMounted(async () => {
  console.log('Workbench mounted. Route ID:', route.params.id)

  if (window.mermaid) {
    window.mermaid.initialize({
      theme: 'dark',
      startOnLoad: false,
      securityLevel: 'loose',
      fontFamily: 'system-ui',
      mindmap: {useMaxWidth: true}
    })
  }

  if (!ensureAuthenticated('请先登录继续使用 LingNow')) {
    return
  }

  if (typeof route.query.p === 'string' && route.query.p.trim() && !prompt.value.trim()) {
    prompt.value = route.query.p.trim()
  }
  loadSeedTrail()

  if (route.params.id) {
    loadProject(route.params.id)
  } else {
    await maybeTriggerSeedPlan()
  }
  fetchHistory() // Ensure history is loaded on mount
  window.addEventListener('message', (event) => {
    if (!event.data || !event.data.type) return
    if (event.data.type === 'lingnow-inspect') {
      inspectedId.value = event.data.id
      selectedNode.value = {
        id: event.data.id,
        text: event.data.text,
        color: event.data.color,
        backgroundColor: event.data.backgroundColor,
        padding: event.data.padding,
        tagName: event.data.tagName
      }
      if (activeTab.value === 'plan') {
        const el = document.getElementById('feature-' + event.data.id)
        if (el) el.scrollIntoView({behavior: 'smooth', block: 'center'})
      }
    } else if (event.data.type === 'lingnow-console') {
      runtimeLogs.value.push({
        level: event.data.level,
        msg: event.data.msg,
        time: new Date().toLocaleTimeString()
      })
      if (runtimeLogs.value.length > 50) runtimeLogs.value.shift()
    }
  })
})

watch(() => route.query.p, (newPrompt) => {
  if (typeof newPrompt === 'string' && newPrompt.trim() && !result.value) {
    prompt.value = newPrompt.trim()
    loadSeedTrail()
    maybeTriggerSeedPlan()
  }
})

onUnmounted(() => {
  stopProjectRefresh()
  if (typeof requestInterceptorId === 'number') {
    axios.interceptors.request.eject(requestInterceptorId)
  }
  if (typeof responseInterceptorId === 'number') {
    axios.interceptors.response.eject(responseInterceptorId)
  }
})

watch(() => route.params.id, (newId) => {
  console.log('Route ID changed to:', newId)
  if (!isAuthenticated.value) {
    if (newId) enterLoginState()
    return
  }
  if (newId) {
    if (!result.value || result.value.id !== newId) {
      loadProject(newId)
    }
  } else {
    resetProject()
  }
})

const currentSessionId = ref(`session-${Date.now()}`)
const generationPhase = ref('idle')

const refreshLoadedProject = async () => {
  if (!ensureAuthenticated()) return
  const id = currentSessionId.value || route.params.id
  if (!id) return
  try {
    const res = await axios.get(`/api/projects/${id}`)
    const normalizedProject = normalizeWorkbenchResult(res.data)
    result.value = normalizedProject
    currentSessionId.value = id
    isDesignStarted.value = !!normalizedProject?.prototypeHtml
    if (normalizedProject?.metaData?.design_ready === 'true' || !['DESIGNING', 'QA'].includes(String(normalizedProject?.status || ''))) {
      fetchHistory()
    }
  } catch (err) {
    if (isAuthError(err)) return
    console.error('Failed to refresh project', err)
  }
}

const startProjectRefresh = () => {
  if (projectRefreshTimer) return
  projectRefreshTimer = window.setInterval(refreshLoadedProject, 5000)
}

const stopProjectRefresh = () => {
  if (!projectRefreshTimer) return
  window.clearInterval(projectRefreshTimer)
  projectRefreshTimer = null
}

watch(isDesignRefining, (refining) => {
  if (refining) {
    startProjectRefresh()
  } else {
    stopProjectRefresh()
  }
}, {immediate: true})

const handleGenerate = async () => {
  if (!ensureAuthenticated()) return
  if (!prompt.value.trim() || loading.value) return
  loading.value = true
  error.value = null
  generationPhase.value = 'PLANNING'

  const sessionId = result.value ? currentSessionId.value : `session-${Date.now()}`
  currentSessionId.value = sessionId
  try {
    const planRes = await axios.post('/api/generate/plan', {
      prompt: prompt.value,
      sessionId: sessionId,
      lang: locale.value
    })

    result.value = normalizeWorkbenchResult(planRes.data)
    pushOperationTrail('完成需求理解')
    if (result.value?.prototypeBundle?.experienceBrief?.referenceSignal) {
      pushOperationTrail(result.value.prototypeBundle.experienceBrief.referenceSignal)
    }
    activeTab.value = 'plan' // Switch to Plan tab to show the mindmap
    isDesignStarted.value = false
    insightDrawerOpen.value = true
    insightContentVisible.value = true
    
    if (route.params.id !== sessionId) {
      router.push(`/project/${sessionId}`)
    }
  } catch (err) {
    if (isAuthError(err)) return
    console.error('Generation failed', err)
    error.value = '规划失败'
  } finally {
    loading.value = false
    generationPhase.value = 'idle'
  }
}

const handleStartDesign = async () => {
  if (!ensureAuthenticated()) return
  if (loading.value) return
  loading.value = true
  generationPhase.value = 'DESIGNING'
  try {
    const activeMindMap = treeData.value ? serializeTree(treeData.value) : result.value?.mindMap
    
    const designRes = await axios.post('/api/generate/design', {
      sessionId: currentSessionId.value,
      lang: locale.value,
      mindMap: activeMindMap
    })
    result.value = normalizeWorkbenchResult(designRes.data)
    pushOperationTrail('确认理解，开始生成原型')
    isDesignStarted.value = true
    activeTab.value = 'design'
    insightDrawerOpen.value = false
    insightContentVisible.value = false
    fetchHistory()
  } catch (err) {
    if (isAuthError(err)) return
    error.value = '视觉设计失败'
  } finally {
    loading.value = false
    generationPhase.value = 'idle'
  }
}

const updateNodeLive = () => {
  if (!selectedNode.value) return
  const iframe = document.querySelector('iframe')
  iframe?.contentWindow?.postMessage({
    type: 'lingnow-update-node',
    ...selectedNode.value
  }, '*')
}

const handleSaveSnapshot = async () => {
  if (!ensureAuthenticated()) return
  try {
    const iframe = document.querySelector('iframe')
    const html = iframe.contentWindow.document.documentElement.outerHTML
    const res = await axios.post('/api/generate/snapshot', {
      sessionId: currentSessionId.value,
      html: html,
      summary: 'Manual refinement'
    })
    result.value = normalizeWorkbenchResult(res.data)
    alert('Version ' + formatVersionLabel(result.value.version) + ' saved successfully!')
  } catch (err) {
    if (isAuthError(err)) return
    alert('Save failed')
  }
}

const handleRollback = async (version) => {
  if (!ensureAuthenticated()) return
  try {
    const res = await axios.post('/api/generate/rollback', {
      sessionId: currentSessionId.value,
      version: version
    })
    result.value = normalizeWorkbenchResult(res.data)
    pushOperationTrail(`回滚到 ${formatVersionLabel(version)}`)
    isDesignStarted.value = !!result.value?.prototypeHtml
    activeTab.value = 'design'
    insightDrawerOpen.value = false
    insightContentVisible.value = false
  } catch (err) {
    if (isAuthError(err)) return
    alert('Rollback failed')
  }
}

const handleConfirm = async () => {
  if (!ensureAuthenticated()) return
  loading.value = true
  generationPhase.value = 'CODING'
  try {
    const response = await axios.post('/api/generate/develop', {
      sessionId: currentSessionId.value
    })
    result.value = normalizeWorkbenchResult(response.data)
    pushOperationTrail('进入源码与交付')
    isDesignStarted.value = true
    activeTab.value = 'preview'
  } catch (err) {
    if (isAuthError(err)) return
    error.value = '编码失败'
  } finally {
    loading.value = false
  }
}

const injectInspectScript = (html, enabled) => {
  if (!html) return html

  // Provide a base tag so the iframe does not try to resolve relative paths
  // against the parent window URL (which confuses Vue Router).
  const baseTag = '<base href="about:blank">';
  const htmlWithBase = html.replace('<head>', '<head>' + baseTag).replace('</head>', '</head>');
  const finalHtml = htmlWithBase.indexOf('<base') === -1 ? baseTag + htmlWithBase : htmlWithBase;

  const script = `
    <script>
      let debugEnabled = ${enabled};
      
      function getStyle(el, prop) {
        return window.getComputedStyle(el).getPropertyValue(prop);
      }

      // Universal Interceptor: Prevent navigation away from prototype
      document.addEventListener('click', (e) => {
        const target = e.target;
        const link = target.closest('a');
        
        // Intercept ALL links to prevent iframe from redirecting the parent Vue app
        if (link) {
          const href = link.getAttribute('href');
          
          if (href && href.startsWith('#')) {
            // It's an internal SPA link (Alpine.js). Prevent browser default which might
            // bubble to parent URL, and manually update the hash instead.
            e.preventDefault();
            window.location.hash = href;
            console.log('[LingNow Prototype] Handled hash navigation:', href);
          } else {
            // It's an external or unknown relative link. Block it.
            if (href && !href.startsWith('javascript:')) {
               e.preventDefault();
               console.log('[LingNow Prototype] Navigation blocked:', href);
            }
          }
        }

        // Inspector logic (only if debug mode is active)
        if (!debugEnabled) return;
        
        const component = target.closest('[data-lingnow-id]')
        if (component) {
          e.preventDefault();
          window.parent.postMessage({ 
            type: 'lingnow-inspect', 
            id: component.getAttribute('data-lingnow-id'),
            text: component.innerText,
            color: getStyle(component, 'color'),
            backgroundColor: getStyle(component, 'background-color'),
            padding: getStyle(component, 'padding'),
            tagName: component.tagName.toLowerCase()
          }, '*')
          
          let badge = document.getElementById('lingnow-spec-badge')
          if (!badge) {
            badge = document.createElement('div')
            badge.id = 'lingnow-spec-badge'
            badge.style.cssText = "position:absolute;background:rgba(37,99,235,0.9);color:white;padding:4px 8px;border-radius:6px;font-size:11px;font-weight:800;z-index:99999;pointer-events:none;box-shadow:0 10px 15px -3px rgba(0,0,0,0.5);"
            document.body.appendChild(badge)
          }
          const rect = component.getBoundingClientRect()
          badge.innerHTML = '<span style="opacity:0.6;margin-right:4px">W x H</span> ' + Math.round(rect.width) + ' x ' + Math.round(rect.height)
          badge.style.top = (rect.top + window.scrollY - 30) + 'px'
          badge.style.left = (rect.left + window.scrollX) + 'px'
          badge.style.display = 'block'
          
          document.querySelectorAll('[data-lingnow-id]').forEach(el => el.style.outline = 'none')
          component.style.outline = '2px solid #3b82f6'
        }
      }, true)

      // Block form submissions from navigating
      document.addEventListener('submit', (e) => {
        e.preventDefault();
        console.log('[LingNow Prototype] Form submission blocked.');
      }, true);

      window.addEventListener('message', (e) => {
        if (e.data.type === 'lingnow-debug') {
          debugEnabled = e.data.enabled;
          if (!debugEnabled) {
             const badge = document.getElementById('lingnow-spec-badge');
             if (badge) badge.style.display = 'none';
             document.querySelectorAll('[data-lingnow-id]').forEach(el => el.style.outline = 'none');
          }
        } else if (e.data.type === 'lingnow-update-node') {
          const el = document.querySelector('[data-lingnow-id="' + e.data.id + '"]')
          if (el) {
            if (e.data.text !== undefined) el.innerText = e.data.text
            if (e.data.color) el.style.color = e.data.color
            if (e.data.backgroundColor) el.style.backgroundColor = e.data.backgroundColor
            if (e.data.padding) el.style.padding = e.data.padding
          }
        }
      })
    <\/script>
  `
  return finalHtml.replace('</body>', script + '</body>')
}

const resetProject = () => {
  prompt.value = ''
  result.value = null
  error.value = null
  generationPhase.value = 'idle'
  activeTab.value = 'plan'
  isDesignStarted.value = false
  insightDrawerOpen.value = false
  seedPlanTriggered.value = false
  if (route.path !== '/workbench') router.push('/workbench')
}

watch([activeTab, () => result.value?.id, () => result.value?.mindMap], async ([tab, id, map]) => {
  if (tab === 'plan' && map) {
    // Only re-parse if we don't have treeData OR if a fundamentally different project is loaded
    // BUT we also need to allow re-parse if map fundamentally changed from outside
    if (!treeData.value || lastParsedId.value !== id) {
      console.log('Project changed or fresh load. Parsing tree data.')
      treeData.value = parseMindMap(map)
      lastParsedId.value = id
    }
  }
}, {immediate: true})
</script>

<template>
  <div class="relative h-screen min-h-screen overflow-hidden bg-[#0f1012] text-gray-200">
    <div class="workspace-grid pointer-events-none absolute inset-0"></div>
    <div class="relative h-full overflow-hidden">
      <div
          class="pointer-events-none absolute left-1/2 top-24 h-[320px] w-[720px] -translate-x-1/2 rounded-full bg-cyan-500/8 blur-[120px]"></div>
      <div
          class="pointer-events-none absolute bottom-[-120px] right-[-80px] h-[360px] w-[360px] rounded-full bg-violet-600/10 blur-[120px]"></div>

      <main class="relative h-full overflow-hidden">
        <div class="absolute left-6 top-6 z-30 flex max-w-[360px] items-center gap-4">
          <button
              class="flex h-10 w-10 shrink-0 items-center justify-center rounded-[20px] border border-white/10 bg-white/5 text-gray-200 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-all hover:border-white/20 hover:bg-white/10"
              @click="resetProject">
            <PanelRight class="h-4 w-4"/>
          </button>
          <h1 class="truncate text-[14px] font-semibold tracking-tight text-white/92">{{ projectLabel }}</h1>
        </div>

        <div v-if="isWorkbenchMode" class="absolute left-1/2 top-6 z-30 hidden -translate-x-1/2 xl:block">
          <div class="flex items-center gap-1 rounded-full border border-white/10 bg-black/40 p-1 backdrop-blur-2xl">
            <button
                v-for="t in [{id: 'plan', icon: Layers, label: '设计理解'},{id: 'design', icon: Layout, label: '原型画布'},{id: 'code', icon: Terminal, label: i18n.tab_source},{id: 'preview', icon: Box, label: i18n.tab_sandbox}]"
                :key="t.id"
                :class="[activeTab === t.id ? 'bg-white text-black shadow-xl' : 'text-gray-400 hover:text-white']"
                class="flex items-center gap-2 rounded-full px-4 py-2 text-xs font-black transition-all"
                @click="activeTab = t.id"
            >
              <component :is="t.icon" class="h-3.5 w-3.5"/>
              {{ t.label }}
            </button>
          </div>
        </div>

        <div class="absolute right-6 top-6 z-30 flex items-center gap-3">
          <button
              class="flex h-12 w-12 items-center justify-center rounded-full border border-white/10 bg-white/5 text-gray-200 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-all hover:border-white/20 hover:bg-white/10">
            <Play class="h-5 w-5"/>
          </button>
          <button
              class="flex items-center gap-3 rounded-full border border-white/10 bg-white/5 px-6 py-3 text-gray-100 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-all hover:border-white/20 hover:bg-white/10">
            <ArrowUpRight class="h-5 w-5"/>
            <span class="text-sm font-black">导出</span>
          </button>
          <button
              class="flex items-center gap-3 rounded-full border border-white/10 bg-white/5 px-6 py-3 text-gray-100 shadow-2xl shadow-black/40 backdrop-blur-2xl transition-all hover:border-white/20 hover:bg-white/10">
            <Share2 class="h-5 w-5"/>
            <span class="text-sm font-black">共享</span>
          </button>
          <div v-if="user"
               class="flex items-center gap-3 rounded-full border border-white/10 bg-white/5 px-3 py-2 shadow-2xl shadow-black/30 backdrop-blur-2xl">
            <button
                class="flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-2 text-[11px] font-black text-gray-200 transition-all hover:border-white/20 hover:bg-white/10"
                @click="toggleLang">
              <Globe class="h-4 w-4 text-gray-500"/>
              {{ locale }}
            </button>
            <button
                class="flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-2 text-[11px] font-black uppercase tracking-widest text-gray-200 transition-all hover:border-blue-400/30 hover:bg-white/10"
                @click="isHistoryOpen = !isHistoryOpen; if(isHistoryOpen) fetchHistory()">
              <History class="h-4 w-4"/>
            </button>
            <button
                v-if="isWorkbenchMode && !isSidebarOpen"
                class="flex items-center gap-2 rounded-full border border-white/10 bg-black/20 px-3 py-2 text-[11px] font-black uppercase tracking-widest text-gray-200 transition-all hover:border-white/20 hover:bg-white/10"
                @click="isSidebarOpen = true">
              <PanelRight class="h-4 w-4"/>
            </button>
            <div
                class="flex h-12 w-12 items-center justify-center rounded-full bg-sky-200/90 text-[16px] font-black text-slate-800">
              {{ user.username?.slice(0, 1)?.toUpperCase() || 'U' }}
            </div>
          </div>
        </div>

        <div v-if="isWorkbenchMode && (activeTab === 'design' || activeTab === 'preview')"
             class="absolute left-1/2 top-20 z-20 flex -translate-x-1/2 items-center gap-3 rounded-full border border-white/10 bg-black/45 px-4 py-3 backdrop-blur-2xl">
          <div class="flex items-center gap-1 rounded-full border border-white/10 bg-white/5 p-1">
            <button
                v-for="d in [{id:'desktop',icon:Monitor,label:'WEB'},{id:'tablet',icon:Tablet,label:'PAD'},{id:'mobile',icon:Smartphone,label:'MOBILE'}]"
                :key="d.id"
                :class="[deviceType === d.id ? 'bg-blue-600 text-white shadow-lg' : 'text-gray-500 hover:text-gray-300']"
                class="flex items-center gap-2 rounded-full px-3 py-2 text-[10px] font-black uppercase tracking-widest transition-all"
                @click="deviceType = d.id">
              <component :is="d.icon" class="h-3.5 w-3.5"/>
              {{ d.label }}
            </button>
          </div>
          <button
              :class="[isDebugMode ? 'border-purple-400/40 bg-purple-500/15 text-purple-200' : 'border-white/10 bg-white/5 text-gray-300 hover:border-white/20']"
              class="flex items-center gap-2 rounded-full border px-4 py-2 text-[10px] font-black uppercase tracking-widest transition-all"
              @click="isDebugMode = !isDebugMode">
            <Sparkles class="h-3.5 w-3.5"/>
            {{ isDebugMode ? '关闭透视' : '开启透视' }}
          </button>
        </div>

        <transition name="drawer">
          <div v-if="hasInsightPayload"
               :class="[insightDrawerOpen ? 'w-[392px] min-h-[56px]' : 'w-[56px] h-[56px]']"
               class="absolute left-6 top-24 z-20 overflow-hidden rounded-[30px] border border-white/10 bg-[#18191b]/94 shadow-2xl shadow-black/60 backdrop-blur-3xl transition-all duration-300">
            <button
                class="absolute left-3 top-3 z-10 flex h-8 w-8 items-center justify-center rounded-2xl bg-white text-black transition-all hover:scale-[0.96]"
                @click="toggleInsightDrawer">
              <div class="flex items-center gap-1">
                <span class="h-1.5 w-1.5 rounded-full bg-black"></span>
                <span class="h-1.5 w-1.5 rounded-full bg-black"></span>
                <span class="h-1.5 w-1.5 rounded-full bg-black"></span>
              </div>
            </button>
            <div v-if="insightContentVisible"
                 class="ml-12 flex items-center justify-between border-b border-white/5 px-5 py-4">
              <span class="text-[12px] font-medium text-gray-400">{{ insightHeaderLabel }}</span>
              <button class="text-white/50 transition hover:text-white" @click="toggleInsightDrawer">⌄</button>
            </div>
            <div v-if="insightContentVisible && hasResolvedInsight"
                 class="max-h-[calc(100vh-180px)] space-y-5 overflow-auto px-5 py-5 no-scrollbar">
              <div class="text-sm leading-7 text-gray-100">{{ insightIntro }}</div>

              <div v-if="insightScreenBullets.length" class="space-y-3 text-sm leading-7 text-gray-300">
                <p class="text-[11px] font-semibold uppercase tracking-[0.24em] text-white/45">{{
                    insightScreenPlanTitle
                  }}</p>
                <ul class="space-y-3">
                  <li v-for="screen in insightScreenBullets" :key="screen.id" class="flex items-start gap-3">
                    <span class="mt-[9px] h-1.5 w-1.5 rounded-full bg-white/70"></span>
                    <div>
                      <span class="font-semibold text-white/95">{{ screen.label }}</span>
                      <span v-if="screen.desc" class="text-gray-300">：{{ screen.desc }}</span>
                    </div>
                  </li>
                </ul>
              </div>

              <div v-if="insightFooterText"
                   class="rounded-[24px] border border-white/15 bg-white/[0.04] px-5 py-5 text-[14px] leading-8 text-gray-200">
                {{ insightFooterText }}
              </div>
            </div>
            <div v-else-if="insightContentVisible" class="px-5 py-5">
              <div
                  class="rounded-2xl border border-dashed border-white/10 bg-black/20 p-4 text-sm leading-7 text-gray-400">
                {{
                  loading && generationPhase === 'PLANNING'
                      ? '正在分析需求，马上给出设计理解和风格说明。'
                      : '输入需求后，这里会先展示设计理解和风格说明，再进入原型生成。'
                }}
              </div>
            </div>
          </div>
        </transition>

        <div class="h-full overflow-auto px-6 pb-44 pt-24 no-scrollbar">
          <div class="relative mx-auto min-h-full max-w-[1600px]">
            <div v-if="activeTab === 'plan'" class="relative min-h-[840px]">
            </div>

            <section v-else-if="activeTab === 'design'" class="space-y-8">
              <div v-if="designStatusText"
                   class="inline-flex items-center gap-2 rounded-full border border-white/10 bg-black/60 px-4 py-2 text-[10px] font-black uppercase tracking-widest text-white shadow-2xl backdrop-blur">
                <Loader2 v-if="isDesignRefining" class="h-3.5 w-3.5 animate-spin text-blue-400"/>
                <span :class="isDesignRefining ? 'text-blue-300' : 'text-emerald-300'">{{ designStatusText }}</span>
              </div>

              <div class="workspace-panel relative min-h-[780px] overflow-hidden rounded-[36px]">
                <div :class="[deviceType === 'desktop' ? 'p-0' : 'p-10']"
                     class="absolute inset-0 flex items-center justify-center overflow-auto no-scrollbar">
                  <div
                      :class="[deviceType === 'desktop' ? 'h-full w-full rounded-none border-0' : (deviceType === 'tablet' ? 'h-[95%] w-[820px] rounded-[2.75rem] border-[12px] border-[#1a1a1a]' : 'h-[90%] w-[390px] rounded-[2.75rem] border-[12px] border-[#1a1a1a]')]"
                      class="relative overflow-hidden bg-white shadow-[0_50px_120px_rgba(0,0,0,0.6)] transition-all duration-700">
                    <iframe :srcdoc="injectInspectScript(result?.prototypeHtml, isDebugMode)"
                            class="absolute inset-0 z-10 h-full w-full border-none bg-white"
                            frameborder="0"></iframe>
                  </div>
                </div>
              </div>
            </section>

            <section v-else-if="activeTab === 'code'" class="workspace-panel rounded-[32px] p-10">
              <div class="flex items-center justify-between">
                <div>
                  <p class="text-[10px] font-black uppercase tracking-[0.25em] text-gray-500">Source Inventory</p>
                  <h3 class="mt-2 text-2xl font-black text-white">源码资产清单</h3>
                </div>
              </div>
              <div class="mt-8 grid gap-4">
                <div v-for="(content, path) in result?.files" :key="path"
                     class="flex items-center justify-between rounded-2xl border border-white/5 bg-black/20 p-4">
                  <div class="flex items-center gap-3">
                    <Terminal class="h-4 w-4 text-gray-500"/>
                    <span class="text-sm font-mono text-gray-200">{{ path }}</span>
                  </div>
                  <span
                      class="rounded-full border border-white/10 px-3 py-1 text-[10px] font-black uppercase tracking-widest text-gray-500">
                    {{ content.length }} bytes
                  </span>
                </div>
              </div>
            </section>

            <section v-else class="workspace-panel overflow-hidden rounded-[36px] p-0">
              <Sandpack
                  :files="sandpackFiles"
                  :options="{ editorHeight: 'calc(100vh - 220px)', externalResources: ['https://cdn.tailwindcss.com'] }"
                  class="h-full"
                  template="vue3"
                  theme="dark"/>
            </section>
          </div>
        </div>

        <div class="absolute bottom-8 left-1/2 z-20 w-[min(680px,calc(100%-2rem))] -translate-x-1/2">
          <div
              class="rounded-[28px] border border-white/10 bg-black/55 px-5 py-4 shadow-2xl shadow-black/50 backdrop-blur-3xl">
            <div class="flex items-start gap-3">
              <textarea
                  v-model="prompt"
                  :placeholder="workbenchPlaceholder"
                  class="h-20 min-h-[80px] flex-1 resize-none bg-transparent text-[15px] leading-7 text-white outline-none placeholder:text-gray-600"></textarea>
              <button
                  :disabled="loading || (!showPlanCta && !prompt.trim())"
                  class="mt-1 inline-flex shrink-0 items-center gap-2 rounded-2xl bg-gradient-to-r from-cyan-500 to-violet-600 px-5 py-3 text-sm font-black text-white transition-all hover:shadow-[0_0_24px_rgba(34,211,238,0.35)] disabled:cursor-not-allowed disabled:opacity-50"
                  @click="showPlanCta ? handleStartDesign() : handleGenerate()">
                <Send class="h-4 w-4"/>
                {{ commandActionLabel }}
              </button>
            </div>
            <div class="mt-4 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <button
                    class="rounded-xl border border-white/10 bg-white/5 p-2.5 text-gray-400 transition-all hover:border-white/20 hover:text-white"
                    title="Upload Reference">
                  <Terminal class="h-4 w-4"/>
                </button>
                <button
                    class="rounded-xl border border-white/10 bg-white/5 p-2.5 text-gray-400 transition-all hover:border-white/20 hover:text-white"
                    title="Voice Command">
                  <Sparkles class="h-4 w-4"/>
                </button>
              </div>
              <span class="text-[11px] text-gray-500">
                {{ result?.id ? '沿用当前项目上下文' : '输入后直接开始规划' }}
              </span>
            </div>
          </div>
        </div>

        <div
            class="absolute bottom-8 left-6 z-20 w-[320px] rounded-[28px] border border-white/10 bg-black/45 px-6 py-5 shadow-2xl shadow-black/50 backdrop-blur-2xl">
          <button class="flex w-full items-center justify-between text-left" @click="logExpanded = !logExpanded">
            <div class="flex items-center gap-3 text-white">
              <Send class="h-4 w-4 text-white/85"/>
              <span class="text-[14px] font-bold tracking-tight">智能体日志</span>
            </div>
            <span :class="logExpanded ? 'rotate-180' : ''"
                  class="text-[14px] text-white/70 transition-transform">⌄</span>
          </button>
        </div>

        <transition name="fade">
          <div v-if="logExpanded"
               class="absolute bottom-28 left-6 z-30 w-[320px] overflow-hidden rounded-[26px] border border-white/10 bg-[#161719]/94 shadow-2xl shadow-black/60 backdrop-blur-3xl">
            <div class="space-y-2 px-4 py-4">
              <div v-for="(item, idx) in logTrailItems" :key="item"
                   :class="[idx === logTrailItems.length - 1 ? 'border-white/20 bg-white/10' : 'border-transparent bg-transparent']"
                   class="flex items-center gap-3 rounded-full border px-4 py-3 text-white/95 transition-all">
                <div
                    class="flex h-5 w-5 items-center justify-center rounded-full border border-white/70 text-[11px] font-black">
                  ✓
                </div>
                <span class="text-[13px] font-medium tracking-tight">{{ item }}</span>
              </div>
              <div v-if="!logTrailItems.length"
                   class="rounded-2xl border border-dashed border-white/10 bg-black/20 p-4 text-xs leading-6 text-gray-500">
                暂无实际操作轨迹，完成生成或继续操作后会在这里出现。
              </div>
            </div>
          </div>
        </transition>

        <div class="absolute right-6 top-1/2 z-20 -translate-y-1/2">
          <div
              class="flex w-16 flex-col items-center gap-6 rounded-[36px] border border-white/10 bg-black/45 px-3 py-6 shadow-2xl shadow-black/50 backdrop-blur-2xl">
            <button class="text-white/90 transition hover:text-white">
              <MousePointer2 class="h-6 w-6"/>
            </button>
            <button class="text-white/80 transition hover:text-white">
              <SquareDashedMousePointer class="h-6 w-6"/>
            </button>
            <button class="rounded-full bg-white p-3 text-slate-900 shadow-lg">
              <Pointer class="h-6 w-6"/>
            </button>
            <button class="text-white/80 transition hover:text-white">
              <Hand class="h-6 w-6"/>
            </button>
            <button class="text-white/80 transition hover:text-white">
              <Image class="h-6 w-6"/>
            </button>
            <div class="h-px w-8 bg-white/15"></div>
            <button class="text-white/80 transition hover:text-white">
              <Palette class="h-6 w-6"/>
            </button>
            <button class="text-white/80 transition hover:text-white">
              <Star class="h-6 w-6"/>
            </button>
          </div>
        </div>

        <div v-if="loading"
             class="absolute inset-0 z-max flex flex-col items-center justify-center gap-6 bg-black/65 backdrop-blur-md">
          <Loader2 class="h-16 w-16 animate-spin text-blue-500"/>
          <h3 class="text-xl font-bold uppercase tracking-widest text-white">{{ generationPhase }} IN PROGRESS...</h3>
          <p class="text-sm text-gray-400">{{ i18n.loading_subtitle }}</p>
        </div>
      </main>

      <transition name="drawer">
        <div v-if="isHistoryOpen"
             class="absolute inset-y-0 right-0 z-[60] w-96 border-l border-white/10 bg-[#0a0a0a]/92 backdrop-blur-3xl shadow-[-20px_0_60px_rgba(0,0,0,0.8)]">
          <div class="flex items-center justify-between border-b border-white/5 p-8">
            <h2 class="text-xl font-black italic tracking-tighter text-white">项目档案库</h2>
            <button @click="isHistoryOpen = false">
              <X class="h-5 w-5"/>
            </button>
          </div>
          <div class="space-y-3 overflow-y-auto p-4 no-scrollbar">
            <div v-for="proj in history" :key="proj.id"
                 class="group cursor-pointer rounded-2xl border border-white/5 bg-white/5 p-5 transition-all hover:border-blue-500/30 hover:bg-white/10"
                 @click="loadProject(proj.id)">
              <div class="mb-2 flex items-start justify-between">
                <p class="flex-1 text-xs font-bold leading-relaxed text-white line-clamp-1">{{ proj.userIntent }}</p>
                <span class="rounded bg-blue-500/10 px-1.5 py-0.5 text-[8px] font-black text-blue-500">{{
                    formatVersionLabel(proj.version)
                  }}</span>
              </div>
              <div class="flex items-center justify-between">
                <span class="text-[9px] font-bold uppercase tracking-widest text-gray-600">{{ proj.status }}</span>
                <span class="text-[8px] text-gray-500">最后编辑: {{ formatDate(proj.lastModified) }}</span>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<style>
.z-max {
  z-index: 9999;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.drawer-enter-active, .drawer-leave-active {
  transition: all 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

.drawer-enter-from, .drawer-leave-to {
  transform: translateX(105%);
  opacity: 0;
}

html, body {
  background: black;
  margin: 0;
  overflow: hidden;
  height: 100vh;
  font-family: sans-serif;
}

#app {
  height: 100vh;
}

.no-scrollbar::-webkit-scrollbar {
  display: none;
}

.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

.workspace-grid {
  background-image: radial-gradient(circle at center, rgba(255, 255, 255, 0.14) 1px, transparent 1px),
  linear-gradient(180deg, rgba(255, 255, 255, 0.025), rgba(255, 255, 255, 0));
  background-size: 18px 18px, 100% 100%;
  opacity: 0.28;
}

.workspace-panel {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(24px);
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.35);
}

/* Tree Styling (Horizontal) */
.tree-node-group {
  min-height: 80px;
}

.tree-branches {
  min-width: 400px;
}
</style>
