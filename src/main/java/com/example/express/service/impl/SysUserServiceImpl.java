package com.example.express.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.express.common.util.CollectionUtils;
import com.example.express.common.util.IDValidateUtils;
import com.example.express.common.util.StringUtils;
import com.example.express.domain.ResponseResult;
import com.example.express.domain.bean.DataSchool;
import com.example.express.domain.bean.SysUser;
import com.example.express.domain.enums.ResponseErrorCodeEnum;
import com.example.express.domain.enums.SysRoleEnum;
import com.example.express.domain.enums.ThirdLoginTypeEnum;
import com.example.express.domain.vo.UserInfoVO;
import com.example.express.mapper.DataSchoolMapper;
import com.example.express.mapper.SysUserMapper;
import com.example.express.service.OrderInfoService;
import com.example.express.service.SmsService;
import com.example.express.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.servlet.http.HttpSession;
import java.util.List;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private DataSchoolMapper dataSchoolMapper;

    @Autowired
    private SmsService smsService;
    @Autowired
    private OrderInfoService orderInfoService;

    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DataSourceTransactionManager transactionManager;

    @Override
    public SysUser getByName(String username) {
        List<SysUser> userList = sysUserMapper.selectList(new QueryWrapper<SysUser>().eq("username", username));

        return CollectionUtils.getListFirst(userList);
    }

    @Override
    public SysUser getByTel(String tel) {
        List<SysUser> userList = sysUserMapper.selectList(new QueryWrapper<SysUser>().eq("tel", tel));

        return CollectionUtils.getListFirst(userList);
    }

    @Override
    public SysUser getByThirdLogin(String thirdLoginId, ThirdLoginTypeEnum thirdLoginTypeEnum) {
        if(thirdLoginTypeEnum == ThirdLoginTypeEnum.NONE) {
            return null;
        }

        List<SysUser> userList = sysUserMapper.selectList(new QueryWrapper<SysUser>()
                .eq("third_login_type", thirdLoginTypeEnum.getType())
                .eq("third_login_id", thirdLoginId));

        return CollectionUtils.getListFirst(userList);
    }

    @Override
    public UserInfoVO getUserInfo(SysUser user) {
        SysRoleEnum userRole = user.getRole();

        UserInfoVO vo = UserInfoVO.builder()
                .username(user.getUsername())
                .sex(String.valueOf(user.getSex().getType()))
                .tel(user.getTel())
                .studentIdCard(user.getStudentIdCard())
                .role(String.valueOf(userRole.getType()))
                .roleName(userRole.getCnName())
                .star(user.getStar())
                .idCard(user.getIdCard())
                .realName(user.getRealName()).build();

        DataSchool school = dataSchoolMapper.selectById(user.getSchoolId());
        if(school != null) {
            vo.setSchool(school.getName());
        }

        if(userRole != SysRoleEnum.USER && userRole != SysRoleEnum.COURIER) {
            vo.setCanChangeRole("0");
        } else {
            if(orderInfoService.isExistUnfinishedOrder(user.getId(), userRole)) {
                vo.setCanChangeRole("0");
            } else {
                vo.setCanChangeRole("1");
            }
        }

        return vo;
    }

    @Override
    public boolean checkExistByTel(String mobile) {
        return getByTel(mobile) != null;
    }

    @Override
    public boolean checkApplyRealName(SysUser user) {
        if(StringUtils.isAnyBlank(user.getRealName(), user.getIdCard())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean checkExistByUsername(String username) {
        return getByName(username) != null;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ResponseResult thirdLogin(String thirdLoginId, ThirdLoginTypeEnum thirdLoginTypeEnum) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(definition);

        SysUser sysUser = getByThirdLogin(thirdLoginId, thirdLoginTypeEnum);
        if(sysUser == null) {
            // 三方注册
            if(!registryByThirdLogin(thirdLoginId, thirdLoginTypeEnum)) {
                transactionManager.rollback(status);
                return ResponseResult.failure(ResponseErrorCodeEnum.REGISTRY_ERROR);
            }
            sysUser = getByThirdLogin(thirdLoginId, thirdLoginTypeEnum);
            if(sysUser == null) {
                transactionManager.rollback(status);
                return ResponseResult.failure(ResponseErrorCodeEnum.THIRD_LOGIN_ERROR);
            }
        }

        transactionManager.commit(status);
        return ResponseResult.success(sysUser);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public ResponseResult registryByUsername(String username, String password) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(definition);

        if(checkExistByUsername(username)) {
            transactionManager.rollback(status);
            return ResponseResult.failure(ResponseErrorCodeEnum.USERNAME_EXIST);
        }

        SysUser user = SysUser.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .role(SysRoleEnum.DIS_FORMAL).build();

        if(!this.retBool(sysUserMapper.insert(user))) {
            transactionManager.rollback(status);
            return ResponseResult.failure(ResponseErrorCodeEnum.REGISTRY_ERROR);
        }

        transactionManager.commit(status);
        return ResponseResult.success();
    }

    @Override
    public ResponseResult registryBTel(String tel, String code, HttpSession session) {
        ResponseErrorCodeEnum codeEnum = smsService.check(session, tel, code);
        if(codeEnum != ResponseErrorCodeEnum.SUCCESS) {
            return ResponseResult.failure(codeEnum);
        }

        SysUser user = SysUser.builder()
                .tel(tel)
                .role(SysRoleEnum.DIS_FORMAL).build();

        if(!this.retBool(sysUserMapper.insert(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.REGISTRY_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult resetPassword(String userId, String oldPassword, String newPassword) {
        SysUser user = getById(userId);

        if(!passwordEncoder.matches(oldPassword, user.getPassword())) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PASSWORD_ERROR);
        }

        user.setPassword(passwordEncoder.encode(newPassword));

        if(!this.retBool(sysUserMapper.updateById(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.PASSWORD_RESET_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult setUsernameAndPassword(SysUser user, String username, String password) {
        if(StringUtils.isNotBlank(user.getUsername())) {
            return ResponseResult.failure(ResponseErrorCodeEnum.USERNAME_DISABLE_MODIFY);
        }

        if(checkExistByUsername(username)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.USERNAME_EXIST);
        }

        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));

        if(!this.retBool(sysUserMapper.updateById(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult setRealName(SysUser user, String realName, String idCard) {
        // 实名信息不支持修改
        if(StringUtils.isNotBlank(user.getIdCard()) || StringUtils.isNotBlank(user.getRealName())) {
            return ResponseResult.failure(ResponseErrorCodeEnum.IDCARD_OR_REALNAME_EXIST);
        }

        if(!IDValidateUtils.check(idCard)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.ID_CARD_INVALID);
        }
        if(StringUtils.containsSpecial(realName)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.REAL_NAME_INVALID);
        }

        user.setRealName(realName);
        user.setIdCard(idCard);
        if(!this.retBool(sysUserMapper.updateById(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult setTel(SysUser user, String tel, String code, HttpSession session) {
        ResponseErrorCodeEnum check = smsService.check(session, tel, code);
        if(check != ResponseErrorCodeEnum.SUCCESS) {
            return ResponseResult.failure(check);
        }

        if(checkExistByTel(tel)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.TEL_EXIST);
        }

        user.setTel(tel);
        if(!this.retBool(sysUserMapper.updateById(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult setSchoolInfo(SysUser user, Integer schoolId, String studentIdCard) {
        DataSchool dataSchool = dataSchoolMapper.selectById(schoolId);
        if(dataSchool == null) {
            return ResponseResult.failure(ResponseErrorCodeEnum.SCHOOL_NOT_EXIST);
        }
        if(!StringUtils.isNumeric(studentIdCard)) {
            return ResponseResult.failure(ResponseErrorCodeEnum.STUDENT_IDCARD_NOT_NUMBER);
        }

        user.setSchoolId(schoolId);
        user.setStudentIdCard(studentIdCard);
        if(!this.retBool(sysUserMapper.updateById(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public ResponseResult changeRole(SysUser user) {
        SysRoleEnum role = user.getRole();
        if(role != SysRoleEnum.USER && role != SysRoleEnum.COURIER) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_NOT_SUPPORT);
        }

        boolean can = orderInfoService.isExistUnfinishedOrder(user.getId(), role);
        if(!can) {
            return ResponseResult.failure(ResponseErrorCodeEnum.EXIST_UNFINISHED_ORDER);
        }

        // user --> courier 需要实名认证
        if(role == SysRoleEnum.USER) {
            if(!checkApplyRealName(user)) {
                return ResponseResult.failure(ResponseErrorCodeEnum.NOT_APPLY_REAL_NAME);
            }
            user.setRole(SysRoleEnum.COURIER);
        } else {
            user.setRole(SysRoleEnum.USER);
        }

        if(!this.retBool(sysUserMapper.updateById(user))) {
            return ResponseResult.failure(ResponseErrorCodeEnum.OPERATION_ERROR);
        }
        return ResponseResult.success();
    }

    @Override
    public String getFrontName(String userId) {
        SysUser sysUser = getById(userId);
        // 获取显示用户名
        String username;
        if(StringUtils.isNotBlank(sysUser.getUsername())) {
            username = sysUser.getUsername();
        } else if(StringUtils.isNotBlank(sysUser.getTel())) {
            username = sysUser.getTel();
        } else {
            username = sysUser.getThirdLogin().getName() + "用户";
        }

        return username;
    }

    private boolean registryByThirdLogin(String thirdLoginId, ThirdLoginTypeEnum thirdLoginTypeEnum) {
        SysUser user = SysUser.builder()
                .thirdLogin(thirdLoginTypeEnum)
                .thirdLoginId(thirdLoginId)
                .role(SysRoleEnum.DIS_FORMAL).build();

        return this.retBool(sysUserMapper.insert(user));
    }
}
