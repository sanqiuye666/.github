use serde::Serialize;
use std::collections::HashMap;

#[derive(Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum EntryMode { Launcher, Updater, Preview }

// This selects a UI only. Java still validates the binding and Bootstrap launch permit.
pub fn classify_entry_args(args: &[String]) -> Result<EntryMode, String> {
    if args.is_empty() { return Ok(EntryMode::Launcher); }
    if args.len() == 1 && args[0] == "--preview" { return Ok(EntryMode::Preview); }
    let invalid = || "启动参数不完整或无效，请从原启动器重新启动。".to_string();
    if args.len() % 2 != 0 { return Err(invalid()); }
    let mut values = HashMap::new();
    for pair in args.chunks_exact(2) {
        let key = pair[0].as_str();
        if !matches!(key, "--bootstrap-port"|"--bootstrap-token"|"--instance"|"--binding"|"--player-name"|"--player-uuid"|"--launcher-brand"|"--launcher-version")
            || values.insert(key, pair[1].as_str()).is_some() { return Err(invalid()); }
    }
    for key in ["--bootstrap-port", "--bootstrap-token", "--instance", "--binding"] {
        if values.get(key).map_or(true, |value| value.trim().is_empty()) { return Err(invalid()); }
    }
    let port = values["--bootstrap-port"].parse::<u32>().map_err(|_| invalid())?;
    if !(1..=65535).contains(&port) { return Err(invalid()); }
    let token = values["--bootstrap-token"];
    if !(32..=128).contains(&token.len()) || !token.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-') { return Err(invalid()); }
    for (key, limit) in [("--player-name",64),("--player-uuid",36),("--launcher-brand",128),("--launcher-version",128)] {
        if let Some(value) = values.get(key) {
            let text = value.trim_matches(|c:char| c <= '\u{20}');
            if text.is_empty() || text.encode_utf16().count() > limit || text.chars().any(char::is_control) { return Err(invalid()); }
            if key == "--player-uuid" && !valid_uuid(text) { return Err(invalid()); }
        }
    }
    Ok(EntryMode::Updater)
}

fn valid_uuid(value: &str) -> bool {
    if value.len() == 32 { return value.bytes().all(|c| c.is_ascii_hexdigit()); }
    value.len() == 36 && value.bytes().enumerate().all(|(i,c)| {
        if [8,13,18,23].contains(&i) { c == b'-' } else { c.is_ascii_hexdigit() }
    })
}

pub fn require_direct_quit(mode:&EntryMode)->Result<(),String> {
    if *mode==EntryMode::Launcher{return Err("请使用窗口关闭按钮，先检查活动游戏与文件任务".into());}Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn valid() -> Vec<String> { vec!["--bootstrap-port".into(),"24680".into(),"--bootstrap-token".into(),"a".repeat(32),"--instance".into(),"G:/instance".into(),"--binding".into(),"G:/binding.json".into()] }
    #[test] fn selects_only_complete_startup_contexts() {
        assert_eq!(classify_entry_args(&[]),Ok(EntryMode::Launcher));
        assert_eq!(classify_entry_args(&["--preview".into()]),Ok(EntryMode::Preview));
        assert_eq!(classify_entry_args(&valid()),Ok(EntryMode::Updater));
    }
    #[test] fn rejects_invalid_context_without_echoing_secrets() {
        let mut missing=valid();missing.truncate(6);
        let mut duplicate=valid();duplicate.extend(["--instance".into(),"other".into()]);
        let mut unknown=valid();unknown.extend(["--admin".into(),"true".into()]);
        for args in [missing,duplicate,unknown,vec!["--preview".into(),"extra".into()]] { assert!(classify_entry_args(&args).is_err()); }
        for port in ["0","65536","bad"] { let mut args=valid();args[1]=port.into();assert!(classify_entry_args(&args).is_err()); }
        let mut args=valid();args[3]="secret-token".into();let message=classify_entry_args(&args).unwrap_err();assert!(!message.contains("secret-token"));
    }
    #[test] fn validates_optional_identity_like_the_java_entry() {
        let mut args=valid();args.extend(["--player-uuid".into(),"01234567-89ab-cdef-0123-456789abcdef".into()]);assert!(classify_entry_args(&args).is_ok());
        *args.last_mut().unwrap()="invalid".into();assert!(classify_entry_args(&args).is_err());
        let mut args=valid();args.extend(["--player-name".into(),"name\nadmin".into()]);assert!(classify_entry_args(&args).is_err());
    }
    #[test] fn launcher_cannot_use_the_updater_direct_exit_command() {
        assert!(require_direct_quit(&EntryMode::Launcher).is_err());
        assert!(require_direct_quit(&EntryMode::Updater).is_ok());
        assert!(require_direct_quit(&EntryMode::Preview).is_ok());
    }
}
