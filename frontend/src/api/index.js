// ============================================================
// API 定义（与后端接口契约一一对应）
// ============================================================
import request from './request'

// ---- 认证 / 菜单 ----
export const login = (data) => request.post('/auth/login', data)
export const getUserInfo = () => request.get('/auth/userinfo')
export const getMenuMine = () => request.get('/menu/mine')

// ---- 系统管理：用户 ----
export const getUserList = (params) => request.get('/user/list', { params })
export const createUser = (data) => request.post('/user', data)
export const updateUser = (id, data) => request.put(`/user/${id}`, data)
export const deleteUser = (id) => request.delete(`/user/${id}`)

// ---- 系统管理：角色授权 ----
export const getRoleMenus = (role) => request.get('/role/menus', { params: { role } })
export const saveRoleMenus = (data) => request.put('/role/menus', data)

// ---- 通用单据引擎 ----
export const getMeta = (docType) => request.get(`/meta/${docType}`)
export const getDocList = (docType, params) => request.get(`/doc/${docType}`, { params })
export const getDocDetail = (docType, id) => request.get(`/doc/${docType}/${id}`)
export const createDoc = (docType, data) => request.post(`/doc/${docType}`, data)
export const updateDoc = (docType, id, data) => request.put(`/doc/${docType}/${id}`, data)
export const deleteDoc = (docType, id) => request.delete(`/doc/${docType}/${id}`)
export const importDoc = (docType, formData) =>
  request.post(`/doc/${docType}/import`, formData)
export const exportDoc = (docType, keyword) =>
  request.get(`/doc/${docType}/export`, { params: { keyword }, responseType: 'blob' })
export const getPref = (docType) => request.get(`/doc/${docType}/pref`)
export const savePref = (docType, columns) => request.put(`/doc/${docType}/pref`, { columns })
