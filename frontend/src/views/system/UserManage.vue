<template>
  <div class="user-page">
    <div class="panel toolbar">
      <div class="panel-title">
        <span class="title-text">用户管理</span>
      </div>
      <div class="toolbar-right">
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="按用户名 / 昵称搜索"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="onSearch">搜索</el-button>
        </div>
        <el-button type="primary" @click="openCreate">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新增用户
        </el-button>
      </div>
    </div>

    <div class="panel table-panel">
      <el-table
        v-loading="loading"
        :data="list"
        empty-text="暂无用户"
        :header-cell-style="headerStyle"
      >
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column prop="role" label="角色" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.role === 'ADMIN' ? 'primary' : 'info'" effect="light" round>
              {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="light" round>
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="230" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openResetPwd(row)">重置密码</el-button>
            <el-button link type="primary" size="small" @click="openRole(row)">设角色</el-button>
            <el-button link type="danger" size="small" @click="removeUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="loadList"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑用户 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" placeholder="登录账号" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="密码" prop="password" :required="!isEdit">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改' : '登录密码'"
          />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="form.role">
            <el-radio-button value="ADMIN">管理员</el-radio-button>
            <el-radio-button value="USER">普通用户</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio-button :value="1">启用</el-radio-button>
            <el-radio-button :value="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保 存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="420px">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="新密码" prop="password">
          <el-input v-model="pwdForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="savePwd">确 定</el-button>
      </template>
    </el-dialog>

    <!-- 设置角色 -->
    <el-dialog v-model="roleVisible" title="设置角色" width="420px">
      <el-form label-width="90px">
        <el-form-item label="用户">
          <span>{{ currentRow?.nickname || currentRow?.username }}</span>
        </el-form-item>
        <el-form-item label="角色">
          <el-radio-group v-model="roleForm.role">
            <el-radio-button value="ADMIN">管理员</el-radio-button>
            <el-radio-button value="USER">普通用户</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">保 存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { getUserList, createUser, updateUser, deleteUser } from '@/api'

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')

const saving = ref(false)
const formVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({ id: null, username: '', nickname: '', password: '', role: 'USER', status: 1 })

const pwdVisible = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({ id: null, password: '' })

const roleVisible = ref(false)
const roleForm = reactive({ id: null, role: 'USER' })
const currentRow = ref(null)

const headerStyle = () => ({ background: 'rgba(245,246,250,0.5)', color: '#6e6e73' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    {
      validator: (_, v, cb) => {
        if (!isEdit.value && (!v || v.length < 4)) return cb(new Error('密码至少 4 位'))
        if (isEdit.value && v && v.length < 4) return cb(new Error('密码至少 4 位'))
        cb()
      },
      trigger: 'blur',
    },
  ],
}
const pwdRules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 4, message: '密码至少 4 位', trigger: 'blur' },
  ],
}

function fmtTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadList() {
  loading.value = true
  try {
    const data = await getUserList({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim(),
    })
    list.value = data?.records || data?.list || data?.rows || (Array.isArray(data) ? data : [])
    total.value = data?.total ?? list.value.length
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}
function onSizeChange() {
  page.value = 1
  loadList()
}

function openCreate() {
  isEdit.value = false
  Object.assign(form, { id: null, username: '', nickname: '', password: '', role: 'USER', status: 1 })
  formVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    username: row.username,
    nickname: row.nickname || '',
    password: '',
    role: row.role || 'USER',
    status: row.status === 1 ? 1 : 0,
  })
  formVisible.value = true
}

async function saveForm() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateUser(form.id, {
        nickname: form.nickname,
        password: form.password || null,
        role: form.role,
        status: form.status,
      })
    } else {
      await createUser({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        role: form.role,
        status: form.status,
      })
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openResetPwd(row) {
  Object.assign(pwdForm, { id: row.id, password: '' })
  pwdVisible.value = true
}

async function savePwd() {
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  saving.value = true
  try {
    await updateUser(pwdForm.id, { password: pwdForm.password })
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function openRole(row) {
  currentRow.value = row
  roleForm.id = row.id
  roleForm.role = row.role || 'USER'
  roleVisible.value = true
}

async function saveRole() {
  saving.value = true
  try {
    await updateUser(roleForm.id, { role: roleForm.role })
    ElMessage.success('角色已更新')
    roleVisible.value = false
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function removeUser(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  if (list.value.length === 1 && page.value > 1) page.value -= 1
  loadList()
}

onMounted(loadList)
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.user-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
}

.panel {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid $color-border;
  border-radius: $radius-md;
  box-shadow: $shadow-card;
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
}

.toolbar {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  .panel-title .title-text {
    font-size: 16px;
    font-weight: 700;
    color: $color-text-primary;
  }

  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;

    .search-box {
      display: flex;
      gap: 8px;
      .search-input {
        width: 260px;
      }
    }
  }
}

.table-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 10px 16px 14px;

  :deep(.el-table) {
    flex: 1;
    min-height: 0;
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    padding-top: 12px;
  }
}
</style>
