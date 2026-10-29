#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    if let Err(error)=dreamingfish_player_lib::run(){dreamingfish_player_lib::report_startup_failure(&error);std::process::exit(1);}
}
