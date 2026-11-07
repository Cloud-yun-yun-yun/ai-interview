import axios from 'axios'
import { ElMessage } from 'element-plus'

//创建axios实例
const service = axios.create({
  timeout: 5000
})
export interface ApiResult<T=any>{
    code:number
    msg:string
    data:T
}

//添加请求拦截器
service.interceptors.request.use(
    config => {
      const token = localStorage.getItem('token')
      if (token) {
          config.headers.Authorization = `Bearer ${token}`
      }
      return config
    },
    error => {
      return Promise.reject(error)
    }
)
//添加响应拦截器
service.interceptors.response.use(
    (response)=>{
        const res = response.data as ApiResult
        if(res&&res.code!==undefined){
            if(res.code===1||res.code===200){
                return res.data
            }else{
                ElMessage.error('请求失败')
                return Promise.reject(new Error( 'Error'))
            }
        }
        return response
    },
    (error) => {
        // 网络层面的错误（404, 500, 断网等）
        ElMessage.error(error.response?.data?.message || '网络请求失败')
        return Promise.reject(error)
    }
)
export default service
