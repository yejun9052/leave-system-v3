import { z } from "zod";

/** 새 비밀번호 규칙 안내(입력 칸 아래) */
export const PASSWORD_RULE_HINT =
  "8자 이상, 영문·숫자·특수문자 중 2종류 이상. 아이디(이메일 @ 앞부분)는 넣을 수 없습니다.";

/**
 * 비밀번호 규칙(서버 PasswordPolicy 와 동일): 8자 이상, UTF-8 72바이트 이하(한글 24자),
 * 영문·숫자·그 밖의 문자 중 2종류 이상, 아이디(이메일 @ 앞부분, 3자 이상일 때) 포함 금지.
 * 맞지 않으면 이유, 맞으면 null. loginId 를 모르면(재설정 링크) 아이디 검사는 서버가 한다.
 */
export function passwordProblem(password: string, loginId?: string): string | null {
  if ([...password].length < 8) return "비밀번호는 8자 이상이어야 합니다.";
  if (new TextEncoder().encode(password).length > 72) {
    return "비밀번호가 너무 깁니다. 영문·숫자·특수문자는 72자, 한글은 24자까지 쓸 수 있습니다.";
  }
  const kinds = [/[A-Za-z]/, /[0-9]/, /[^A-Za-z0-9]/].filter((re) => re.test(password)).length;
  if (kinds < 2) return "영문, 숫자, 특수문자 중 2종류 이상을 섞어 주세요.";
  const id = (loginId ?? "").split("@")[0].trim().toLowerCase();
  if (id.length >= 3 && password.toLowerCase().includes(id)) {
    return "비밀번호에 아이디(이메일 @ 앞부분)를 넣을 수 없습니다.";
  }
  return null;
}

const newPasswordField = (loginId?: string) =>
  z.string().superRefine((v, ctx) => {
    const problem = passwordProblem(v, loginId);
    if (problem) ctx.addIssue({ code: z.ZodIssueCode.custom, message: problem });
  });

/** 새 비밀번호 + 확인(재설정 링크: 누구의 계정인지 화면은 모른다) */
export const newPasswordSchema = z
  .object({
    newPassword: newPasswordField(),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    message: "새 비밀번호가 일치하지 않습니다.",
    path: ["confirmPassword"],
  });

/** 현재 비밀번호 + 새 비밀번호 + 확인. loginId: 로그인한 사람의 이메일(관리 계정은 아이디) */
export const changePasswordSchema = (loginId?: string) =>
  z
    .object({
      currentPassword: z.string().min(1, "현재 비밀번호를 입력하세요."),
      newPassword: newPasswordField(loginId),
      confirmPassword: z.string(),
    })
    .refine((v) => v.newPassword === v.confirmPassword, {
      message: "새 비밀번호가 일치하지 않습니다.",
      path: ["confirmPassword"],
    })
    .refine((v) => v.newPassword !== v.currentPassword, {
      message: "현재 비밀번호와 다른 비밀번호를 입력하세요.",
      path: ["newPassword"],
    });

export type ChangePasswordValues = z.infer<ReturnType<typeof changePasswordSchema>>;
export type NewPasswordValues = z.infer<typeof newPasswordSchema>;
