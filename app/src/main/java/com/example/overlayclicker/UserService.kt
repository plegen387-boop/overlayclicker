package com.example.overlayclicker

class UserService : IUserService.Stub() {

    override fun destroy() {
        System.exit(0)
    }

    override fun exit() {
        System.exit(0)
    }

    override fun doSomething(): String {
        return "Shizuku servisi çalışıyor."
    }
}
