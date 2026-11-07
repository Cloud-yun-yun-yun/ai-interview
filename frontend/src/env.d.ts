/// <reference types="vite/client" />

export {}

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guest?: boolean
    title?: string
    icon?: string
  }
}