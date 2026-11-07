import service from '@/utils/request'

export interface LoginForm{
    username:string
    password:string
}
export interface UserLoginVO{
    id?:number
    token:string
    activeSessionId?:string|null
}
export interface RegisterForm{
    username:string
    password:string
    verifyPassword:string
}
export function login(loginForm:LoginForm){
    return service.post<UserLoginVO>('/api/user/login',loginForm)
}
export function register(registerForm:RegisterForm){
    return service.post('/api/user/register',registerForm)
}