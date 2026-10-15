package com.lms.notificationservice.service;
import com.lms.notificationservice.exception.EmailSendException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    @Value("${notification.mail.from}")
    private String fromAddress;

    public void sendHtmlEmail(String to,String subject,String templateName,Context context){
        if(to==null || to.isBlank()){
        throw new EmailSendException("Alıcı e-posta adresi boş");
        }
        try{
            String html= templateEngine.process(templateName,context);
            MimeMessage message= mailSender.createMimeMessage();
            MimeMessageHelper helper= new MimeMessageHelper(message,true,"UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html,true);
            mailSender.send(message);
            log.info("E-posta gönderildi: '{}' ->{} ",subject,to);


        }
        catch (MessagingException | MailException e){
            throw new EmailSendException("e-posta gönderilemedi"+e.getMessage());
        }




    }





}
