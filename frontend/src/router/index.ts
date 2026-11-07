import { createRouter, createWebHistory } from 'vue-router'
import InterviewLayout from '@/layout/InterviewLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Root',
      component: () => import('@/views/login.vue'), // 随便一个组件，实际会被重定向走
      meta: { root: true },
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/login.vue'),
      meta: { guest: true }
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/register.vue'),
      meta: { guest: true }
    },
    {
      path: '/backend',
      component: InterviewLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '/backend/upload',
          name: 'upload',
          component: () => import('@/views/resumeUpload.vue'),
          meta: {
            title: '上传简历',
            icon: 'DocumentAdd'
          }
        },
        {
          path: '/backend/resume',
          name: 'resume',
          component: () => import('@/views/resume.vue'),
          meta: {
            title: '简历管理',
            icon: 'Files'
          }
        },
        {
          path: '/backend/interview',
          name: 'interview',
          component: () => import('@/views/interview.vue'),
          meta: {
            title: '模拟面试',
            icon: 'Monitor'
          }
        },
        {
          path: '/backend/record',
          name: 'record',
          component: () => import('@/views/record.vue'),
          meta: {
            title: '面试记录',
            icon: 'List'
          }
        },
        {
          path: '/backend/knowledgeBase',
          name: 'knowledgeBase',
          component: () => import('@/views/knowledgeBase.vue'),
          meta: {
            title: '面试知识库',
            icon: 'Reading'
          }
        },
        {
          path: '/backend/knowledgeBase/upload',
          name: 'knowledgeBaseUpload',
          component: () => import('@/views/knowledgeBaseUpload.vue'),
          meta: {
            title: '上传知识库',
            icon: 'Upload'
          }
        },
        {
          path: 'resume/detail/:id',
          name: 'resumeDetail',
          component: () => import('@/views/resumeDetail.vue'),
          meta: {
            title: '简历详情',
            icon: 'DocumentDetail'
          }
        },
        {
          path: 'interview/exam',
          name: 'interviewExam',
          component: () => import('@/views/interviewExam.vue'),
          meta: {
            title: '面试考试',
            icon: 'Monitor'
          }
        },
      ]
    }
  ]
})

function isAuthenticated(): boolean {
  return !!localStorage.getItem('token')
}

router.beforeEach((to, _from) => {
  if (to.meta.root) {
    return isAuthenticated() ? { path: '/backend/upload' } : { name: 'Login' }
  }
  if (to.meta.requiresAuth && !isAuthenticated()) {
    const redirectPath = to.fullPath === '/login' ? undefined : to.fullPath;
    return { path: '/login', query: { redirect: redirectPath } }
  }
  if (to.meta.guest && isAuthenticated()) {
    return { path: '/backend/upload' }
  }
  return true
})

export default router