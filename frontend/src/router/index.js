import {createRouter, createWebHistory} from 'vue-router'
import Workbench from '../views/Workbench.vue'
import LoginView from '../views/LoginView.vue'

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
