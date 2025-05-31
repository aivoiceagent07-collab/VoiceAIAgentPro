
import React from 'react';
import Header from '@/components/Header';
import Hero from '@/components/Hero';
import Features from '@/components/Features';
import MadeInIndia from '@/components/MadeInIndia';
import ContactForm from '@/components/ContactForm';
import Footer from '@/components/Footer';

const Index = () => {
  return (
    <div className="min-h-screen bg-white">
      <Header />
      <Hero />
      <Features />
      <MadeInIndia />
      <ContactForm />
      <Footer />
    </div>
  );
};

export default Index;
