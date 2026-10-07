// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.clubdesk;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 门店会员、教练、教室、课程和卡套餐的真实维护，引用和版本保护。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
@Service
@Transactional
public class MasterService {
  final Store db;
  final AccessService access;

  public MasterService(Store db, AccessService access) {
    this.db = db;
    this.access = access;
  }

  /** 明确可维护字段，不接受金额余额或已使用课时。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public record Input(
      String code,
      String name,
      String nameEn,
      Long departmentId,
      String contactNote,
      String specialty,
      Long categoryId,
      Integer durationMinutes,
      Integer capacity,
      String mode,
      Integer credits,
      Integer validDays,
      BigDecimal price,
      Boolean enabled,
      Long revision) {}

  /** 新增或修改本门店资料，外部账号不获得员工权限。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public Object save(String type, Long id, Input v) {
    db.lock(Department.class, 1L);
    access.require("master");
    var d = db.get(Department.class, v.departmentId);
    access.department(d.id);
    if (!d.enabled) throw new Problem(409, "BRANCH_DISABLED");
    var enabled = Boolean.TRUE.equals(v.enabled);
    var name = AdminService.text(v.name, 160);
    Object result;
    switch (type) {
      case "members" -> {
        var x = id == null ? new Member() : db.get(Member.class, id);
        verify(id, x.departmentId, x.revision, v);
        var code = code(v.code);
        if (id != null && !code.equals(x.code)) throw new Problem(409, "CODE_IMMUTABLE");
        if (id != null
            && !enabled
            && db.query(
                        Reservation.class,
                        "from Reservation where memberId=?1 and state='BOOKED'",
                        id)
                    .size()
                > 0) throw new Problem(409, "ACTIVE_BOOKINGS");
        x.code = code;
        x.name = name;
        x.departmentId = d.id;
        x.contactNote = optional(v.contactNote, 300);
        x.enabled = enabled;
        x.revision++;
        result = id == null ? db.save(x) : x;
      }
      case "coaches" -> {
        var x = id == null ? new Coach() : db.get(Coach.class, id);
        verify(id, x.departmentId, x.revision, v);
        var code = code(v.code);
        if (id != null && !code.equals(x.code)) throw new Problem(409, "CODE_IMMUTABLE");
        if (id != null && !enabled && hasSessions("coachId", id))
          throw new Problem(409, "RESOURCE_IN_USE");
        x.code = code;
        x.name = name;
        x.departmentId = d.id;
        x.specialty = optional(v.specialty, 300);
        x.enabled = enabled;
        x.revision++;
        result = id == null ? db.save(x) : x;
      }
      case "rooms" -> {
        var x = id == null ? new Room() : db.get(Room.class, id);
        verify(id, x.departmentId, x.revision, v);
        var capacity = ClubPolicy.number(v.capacity, 1, 100);
        if (id != null
            && hasSessions("roomId", id)
            && (!enabled
                || db
                    .query(
                        ClubSession.class,
                        "from ClubSession where roomId=?1 and state in ('DRAFT','PUBLISHED')",
                        id)
                    .stream()
                    .anyMatch(s -> s.capacity > capacity)))
          throw new Problem(409, "RESOURCE_IN_USE");
        x.name = name;
        x.departmentId = d.id;
        x.capacity = capacity;
        x.enabled = enabled;
        x.revision++;
        result = id == null ? db.save(x) : x;
      }
      case "courses" -> {
        var x = id == null ? new Course() : db.get(Course.class, id);
        verify(id, x.departmentId, x.revision, v);
        var category = db.get(DictionaryEntry.class, v.categoryId);
        if (!category.enabled || !category.type.equals("courseCategory"))
          throw new Problem(409, "CATEGORY_DISABLED");
        x.name = name;
        x.nameEn = AdminService.text(v.nameEn, 160);
        x.departmentId = d.id;
        x.categoryId = category.id;
        x.durationMinutes = ClubPolicy.number(v.durationMinutes, 5, 480);
        x.capacity = ClubPolicy.number(v.capacity, 1, 100);
        x.enabled = enabled;
        x.revision++;
        result = id == null ? db.save(x) : x;
      }
      case "plans" -> {
        var x = id == null ? new Plan() : db.get(Plan.class, id);
        verify(id, x.departmentId, x.revision, v);
        if (v.mode == null || !Set.of("CREDITS", "PERIOD").contains(v.mode))
          throw new Problem(400, "INVALID_INPUT");
        x.name = name;
        x.nameEn = AdminService.text(v.nameEn, 160);
        x.departmentId = d.id;
        x.mode = v.mode;
        x.credits =
            ClubPolicy.number(
                v.credits, v.mode.equals("PERIOD") ? 0 : 1, v.mode.equals("PERIOD") ? 0 : 9999);
        x.validDays = ClubPolicy.number(v.validDays, 1, 730);
        x.price = ClubPolicy.money(v.price);
        x.enabled = enabled;
        x.revision++;
        result = id == null ? db.save(x) : x;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.audit("MASTER_SAVE_" + type, id == null ? "NEW" : id, d.id);
    return result;
  }

  /** 只删除没有被业务、账号或其他资料引用的档案，保留业务历史。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public void delete(String type, Long id, Long revision) {
    db.lock(Department.class, 1L);
    access.require("master");
    Object x;
    Long dep;
    long ver;
    switch (type) {
      case "members" -> {
        var m = db.get(Member.class, id);
        x = m;
        dep = m.departmentId;
        ver = m.revision;
      }
      case "coaches" -> {
        var m = db.get(Coach.class, id);
        x = m;
        dep = m.departmentId;
        ver = m.revision;
      }
      case "rooms" -> {
        var m = db.get(Room.class, id);
        x = m;
        dep = m.departmentId;
        ver = m.revision;
      }
      case "courses" -> {
        var m = db.get(Course.class, id);
        x = m;
        dep = m.departmentId;
        ver = m.revision;
      }
      case "plans" -> {
        var m = db.get(Plan.class, id);
        x = m;
        dep = m.departmentId;
        ver = m.revision;
      }
      default -> throw new Problem(404, "NOT_FOUND");
    }
    access.department(dep);
    version(ver, revision);
    access.audit("MASTER_DELETE_" + type, id, dep);
    db.delete(x);
  }

  /** 批量新增会员，任一行失败使整批会员及审计一起回滚。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public List<?> importMembers(List<Input> rows) {
    if (rows == null || rows.isEmpty() || rows.size() > 300)
      throw new Problem(400, "INVALID_IMPORT");
    var result = new ArrayList<>();
    int index = 0;
    for (var row : rows) {
      index++;
      try {
        result.add(save("members", null, row));
        db.flush();
      } catch (Problem e) {
        throw new ImportProblem(index, e.getMessage());
      } catch (org.springframework.dao.DataIntegrityViolationException
          | jakarta.persistence.PersistenceException e) {
        throw new ImportProblem(index, "CONFLICT");
      }
    }
    return result;
  }

  /** 被未结束课次引用的教练和教室不能直接停用。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private boolean hasSessions(String field, Long id) {
    return !db.query(
            ClubSession.class,
            "from ClubSession where " + field + "=?1 and state in ('DRAFT','PUBLISHED')",
            id)
        .isEmpty();
  }

  /** 已有档案固定门店，更新须核对当前版本。 官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  private void verify(Long id, Long department, long revision, Input v) {
    if (id != null) {
      access.department(department);
      if (!Objects.equals(department, v.departmentId)) throw new Problem(409, "BRANCH_IMMUTABLE");
      version(revision, v.revision);
    }
  }

  /** 所有版本检查明确拒绝过期输入，不自动覆盖其他操作。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static void version(long actual, Long expected) {
    if (expected == null || expected != actual) throw new Problem(409, "VERSION_CONFLICT");
  }

  /** 固定会员与教练代码，避免数据库大小写排序差异导致重复。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String code(String v) {
    v = AdminService.text(v, 60).toUpperCase(Locale.ROOT);
    if (!v.matches("[A-Z0-9_.-]{2,60}")) throw new Problem(400, "INVALID_CODE");
    return v;
  }

  /** 可选说明限制长度，空值归一为空文本。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  public static String optional(String v, int max) {
    if (v == null) return "";
    if (v.length() > max) throw new Problem(400, "INVALID_INPUT");
    return v.trim();
  }
}
