package org.example.settings.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.settings.entity.SystemSetting;
import org.example.settings.mapper.SystemSettingMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 系统配置读写：key-value 存 t_system_setting，供 AI 与测试代码输出路径等使用。表不存在时静默返回空。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SystemSettingMapper systemSettingMapper;

    public Map<String, String> getAll() {
        try {
            List<SystemSetting> list = systemSettingMapper.selectList(new LambdaQueryWrapper<>());
            Map<String, String> map = new HashMap<>();
            for (SystemSetting s : list) {
                map.put(s.getConfigKey(), s.getConfigValue());
            }
            return map;
        } catch (DataAccessException e) {
            log.debug("读取系统配置失败（可能表未初始化）: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public String get(String key) {
        try {
            LambdaQueryWrapper<SystemSetting> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemSetting::getConfigKey, key).last("limit 1");
            SystemSetting s = systemSettingMapper.selectOne(wrapper);
            return s != null ? s.getConfigValue() : null;
        } catch (DataAccessException e) {
            return null;
        }
    }

    public void save(Map<String, String> settings) {
        try {
            doSave(settings);
        } catch (DataAccessException e) {
            log.warn("保存系统配置失败: {}", e.getMessage());
        }
    }

    private void doSave(Map<String, String> settings) {
        LocalDateTime now = LocalDateTime.now();
        settings.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            LambdaQueryWrapper<SystemSetting> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SystemSetting::getConfigKey, key).last("limit 1");
            SystemSetting existing = systemSettingMapper.selectOne(wrapper);
            if (existing == null) {
                if (value == null) {
                    return;
                }
                SystemSetting s = new SystemSetting();
                s.setConfigKey(key);
                s.setConfigValue(value);
                s.setCreatedAt(now);
                s.setUpdatedAt(now);
                systemSettingMapper.insert(s);
            } else {
                existing.setConfigValue(value);
                existing.setUpdatedAt(now);
                systemSettingMapper.updateById(existing);
            }
        });
    }
}

