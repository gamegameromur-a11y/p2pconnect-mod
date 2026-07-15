package com.p2pconnect.mod.util;

/**
 * Bir kullanıcı bir istemi kabul ettiğinde ("Kabul Et" butonuna bastığında),
 * dünya oluşturma ekranına yönlendirilir. Dünya oluşturulup oyuna girildiğinde
 * (AutoHostTrigger tarafından tespit edilir) otomatik olarak hosting başlatılıp
 * istek sahibine bağlantı bilgisi gönderilmesi gerekir. Bu durumu ekranlar arası
 * taşımak için basit bir static state kullanıyoruz.
 */
public class PendingState {
    /** Kabul edilen isteği gönderen kullanıcı adı. Null ise bekleyen bir "otomatik host" yok. */
    public static volatile String pendingAutoHostForRequester = null;
}
