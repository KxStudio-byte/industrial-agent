<template>
  <el-dialog
    :model-value="modelValue"
    title="新增设备"
    width="720px"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="96px"
      label-position="right"
      @submit.prevent
    >
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备编号" prop="code">
            <el-input v-model="form.code" placeholder="如 EQ-IM-049" clearable maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备名称" prop="name">
            <el-input v-model="form.name" placeholder="如 9号注塑机" clearable maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="所属产线" prop="lineId">
            <el-select
              v-model="form.lineId"
              placeholder="请选择产线"
              style="width: 100%"
              :loading="optionsLoading"
            >
              <el-option
                v-for="l in lines"
                :key="l.id"
                :label="l.lineName"
                :value="l.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="设备类型" prop="type">
            <el-select v-model="form.type" placeholder="请选择类型" style="width: 100%" clearable>
              <el-option v-for="t in EQUIPMENT_TYPES" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="型号" prop="model">
            <el-input v-model="form.model" placeholder="如 IM-220T" clearable maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="制造厂商" prop="vendor">
            <el-input v-model="form.vendor" placeholder="如 海天" clearable maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="健康度" prop="health">
            <el-input-number
              v-model="form.health"
              :min="0"
              :max="100"
              :step="1"
              controls-position="right"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="风险等级" prop="riskLevel">
            <el-select v-model="form.riskLevel" placeholder="请选择风险等级" style="width: 100%">
              <el-option v-for="r in RISK_LEVELS" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="设备状态" prop="status">
            <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
              <el-option v-for="s in STATUSES" :key="s" :label="s" :value="s" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="最近异常" prop="lastException">
        <el-input
          v-model="form.lastException"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="选填，如：主轴轴承振动持续升高"
        />
      </el-form-item>

      <el-alert
        v-if="serverError"
        type="error"
        :title="serverError"
        :closable="false"
        show-icon
        class="form-error"
      />
    </el-form>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确认新增</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createEquipment,
  fetchEquipmentOptions,
  type LineOption
} from '../../api/equipment'

/**
 * 新增设备弹窗。
 * <p>
 * 字段与 {@code base_equipment} 表 / {@code EquipmentCreateRequest} 一一对应，没有多余字段：
 * code / name / lineId / type / model / vendor / health / riskLevel / status / lastException。
 * 其中 **code、name、lineId 为后端 @NotBlank/@NotNull 必填**，前端同步做必填校验，
 * 其余为选填（不填时后端用默认值：health=100、riskLevel=低、status=正常）。
 */

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const serverError = ref('')
const optionsLoading = ref(false)
const lines = ref<LineOption[]>([])

/** 设备类型取自库里实际出现的值，与种子数据保持一致 */
const EQUIPMENT_TYPES = [
  '注塑', '冲压', '装配', '焊接', '检测', '包装', '物流', '机器人', '动力', '辅机', '专用'
]
const RISK_LEVELS = ['高', '中', '低']
const STATUSES = ['正常', '预警', '故障', '离线']

function blank() {
  return {
    code: '',
    name: '',
    lineId: undefined as number | undefined,
    type: '',
    model: '',
    vendor: '',
    health: 100,
    riskLevel: '低',
    status: '正常',
    lastException: ''
  }
}

const form = reactive(blank())

/**
 * 校验规则，与后端 EquipmentCreateRequest 的注解逐条对应。
 * <p>
 * 前端校验不是为了「防住」调用方（后端已经防了），而是为了
 * 在提交前就把能本地判断的错指出来，省一次往返 —— 尤其是编号格式这种
 * 纯格式问题，没必要让用户等一次 500ms 的请求才发现写错了。
 */
const rules: FormRules = {
  code: [
    { required: true, message: '设备编号不能为空', trigger: 'blur' },
    { max: 50, message: '设备编号长度不能超过 50', trigger: 'blur' },
    {
      pattern: /^[A-Za-z0-9_-]{2,50}$/,
      message: '设备编号只能包含字母、数字、下划线和短横线，长度 2-50',
      trigger: 'blur'
    }
  ],
  name: [
    { required: true, message: '设备名称不能为空', trigger: 'blur' },
    { max: 50, message: '设备名称长度不能超过 50', trigger: 'blur' }
  ],
  lineId: [{ required: true, message: '所属产线不能为空', trigger: 'change' }],
  type: [{ max: 50, message: '设备类型长度不能超过 50', trigger: 'blur' }],
  model: [{ max: 50, message: '型号长度不能超过 50', trigger: 'blur' }],
  vendor: [{ max: 50, message: '厂商长度不能超过 50', trigger: 'blur' }],
  health: [
    {
      type: 'number',
      min: 0,
      max: 100,
      message: '健康度必须在 0 ~ 100 之间',
      trigger: 'change'
    }
  ],
  riskLevel: [
    { pattern: /^(高|中|低)$/, message: '风险等级只能是 高 / 中 / 低', trigger: 'change' }
  ],
  status: [
    {
      pattern: /^(正常|预警|故障|离线)$/,
      message: '设备状态只能是 正常 / 预警 / 故障 / 离线',
      trigger: 'change'
    }
  ],
  lastException: [{ max: 200, message: '最近异常描述长度不能超过 200', trigger: 'blur' }]
}

/** 弹窗打开时重置表单并拉取产线（每次都拉，保证新增了产线不用刷新页面） */
async function onOpen() {
  serverError.value = ''
  Object.assign(form, blank())
  formRef.value?.clearValidate()
  if (lines.value.length === 0) {
    optionsLoading.value = true
    try {
      const opt = await fetchEquipmentOptions()
      lines.value = opt.lines ?? []
    } catch {
      // 产线拉不到不算致命：用户点确认时后端会再校验，这里只提示一句
      ElMessage.warning('产线列表加载失败，请检查后端服务')
    } finally {
      optionsLoading.value = false
    }
  }
}

function onCancel() {
  emit('update:modelValue', false)
}

async function onSubmit() {
  if (!formRef.value) return
  serverError.value = ''

  // validate 抛异常表示校验没过，此时不要发请求
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    // 选填字段空串要转成 undefined，否则后端 @Size 校验虽然过，
    // 但库里会存一堆 '' 而不是 NULL，后续查询和展示都会多一层判断
    const payload = {
      code: form.code.trim(),
      name: form.name.trim(),
      lineId: form.lineId,
      type: form.type || undefined,
      model: form.model.trim() || undefined,
      vendor: form.vendor.trim() || undefined,
      health: form.health ?? 100,
      riskLevel: form.riskLevel || undefined,
      status: form.status || undefined,
      lastException: form.lastException.trim() || undefined
    }
    await createEquipment(payload)
    ElMessage.success(`设备「${form.name.trim()}」新增成功`)
    emit('update:modelValue', false)
    emit('success')
  } catch (e) {
    // request.ts 已把后端的 msg 转成 Error.message（如「设备编号已存在：EQ-IM-049」），
    // 直接展示，不要用笼统的「提交失败」盖掉真实原因
    const msg = e instanceof Error ? e.message : '新增设备失败'
    serverError.value = msg
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

/** 供父组件判断是否有未提交内容（当前未使用，保留扩展） */
const dirty = computed(() => JSON.stringify(form) !== JSON.stringify(blank()))
defineExpose({ dirty })
</script>

<style scoped>
.form-error {
  margin-top: 4px;
}
</style>