import {createRouter, createWebHistory} from 'vue-router'
import Workbench from '../views/Workbench.vue'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import DocsView from '../views/DocsView.vue'
import SolutionsHubView from '../views/SolutionsHubView.vue'
import AboutView from '../views/AboutView.vue'
import UpdatesView from '../views/UpdatesView.vue'

const hasToken = () => {
    try {
        const user = JSON.parse(localStorage.getItem('user') || 'null')
        return !!user?.token
    } catch (e) {
        return false
    }
}

const routes = [
    {
        path: '/',
        name: 'Home',
        component: HomeView,
        meta: {requiresAuth: false}
    },
    {
        path: '/workbench',
        name: 'Workbench',
        component: Workbench,
        meta: {requiresAuth: true}
    },
    {
        path: '/project/:id',
        name: 'Project',
        component: Workbench,
        props: true,
        meta: {requiresAuth: true}
    },
    {
        path: '/login',
        name: 'Login',
        component: LoginView
    },
    {
        path: '/docs',
        name: 'Docs',
        component: DocsView
    },
    {
        path: '/solutions',
        name: 'Solutions',
        component: SolutionsHubView
    },
    {
        path: '/about',
        name: 'About',
        component: AboutView
    },
    {
        path: '/updates',
        name: 'Updates',
        component: UpdatesView
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const authenticated = hasToken()

    if (to.meta.requiresAuth && !authenticated) {
        next({
            name: 'Login',
            query: {
                redirect: to.fullPath
            }
        })
        return
    }

    if (to.name === 'Login' && authenticated) {
        const redirect = typeof to.query.redirect === 'string' && to.query.redirect.startsWith('/')
            ? to.query.redirect
            : '/'
        next(redirect)
        return
    }

    next()
})

export default router
