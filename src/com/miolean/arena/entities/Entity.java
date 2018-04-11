package com.miolean.arena.entities;

import java.awt.*;

import static com.miolean.arena.framework.Global.ARENA_SIZE;
import static com.miolean.arena.framework.Global.BORDER;

/**
 * Created by commandm on 2/16/17.
 * Anything that moves according to set physics is an Entity.
 * Entities can update themselves every tick and render themselves.
 * They can also interact with Entities with which they intersect.
 */

public abstract class Entity {

    //Motion components:
    private double x; //X position, in pixels
    private double y; //Y position, in pixels
    private double r; //Rotation, in radians
    private double velX; //X velocity, in pixels per tick.
    private double velY; //Y velocity, in pixels per tick.
    private double velR; //Rotational velocity, in degrees per tick.
    private double accX; //X acceleration, in pixels per tick per tick.
    private double accY; //Y acceleration, in pixels per tick per tick.
    private double accR; //Rotational acceleration, in degrees per tick per tick.

    private final static double DRAG = 0.1; //The amount that an Entity naturally slows down each tick, per unit of velocity.
    private final static double RDRAG = 0.5;

    //Size components:
    private int width;
    private int height;
    private double mass = 1;

    //Entities can also be destroyed:
    private double health = 1;

    //Other things:
    private Field field;

    //ID management
    private int uuid = -1;


    Entity(int width, int height, int health, Field field) {
        this.width = width;
        this.height = height;
        this.health = health;
        this.field = field;
    }


    void applyPhysics() {
        r %= 6.28;

        velX -= DRAG * velX;
        velY -= DRAG * velY;
        velR -= RDRAG * velR;

        velX += accX;
        velY += accY;
        velR += accR;

        x += velX;
        y += velY;
        r += velR;

        if(x > ARENA_SIZE - BORDER) x = ARENA_SIZE - BORDER;
        if(x < BORDER) x = BORDER;
        if(y > ARENA_SIZE - BORDER) y = ARENA_SIZE - BORDER;
        if(y < BORDER) y = BORDER;
    }

    void repel(Entity e) {
        double compoundVel = Math.sqrt(velX*velX + velY*velY);
        double relX = x - e.getX();
        double relY = y  - e.getY();

        double collisionAngle = Math.atan(relY/relX);
        double forceAngle = ((velX > 0)? Math.PI:0) + Math.atan(velY/velX);
        double reflectAngle = 2*collisionAngle-forceAngle;

        double percentMass = mass / (mass + e.getMass()); //basically for translating momentum into force

        force(compoundVel * (1-percentMass), reflectAngle);
        e.force(compoundVel * percentMass, reflectAngle + Math.PI);
    }

    void force(double direction, double magnitude) {
        accX += magnitude * Math.cos(direction) / mass;
        accY += magnitude * Math.sin(direction) / mass;
    }

    void tick() {
        update();
        if(health <= 0) field.remove(this);
    }

    public abstract void render(Graphics g);
    protected abstract void update();
    public abstract boolean intersectsWith(Entity e);
    public abstract void intersect(Entity e);
    protected abstract void onBirth();
    protected abstract void onDeath();
    public abstract String toHTML();

    public boolean isAlive() {return (health > 0);}
    public double getX() { return x; }
    public double getY() { return y; }
    public double getR() { return r; }
    public double getVelX() { return velX; }
    public double getVelY() { return velY; }
    public double getVelR() { return velR; }
    public double getAccX() { return accX; }
    public double getAccY() { return accY; }
    public double getAccR() { return accR; }
    public double getHealth() { return health; }
    public double getMass() { return mass; }
    public int getUUID() { return uuid; }
    public int getWidth() { return width; }
    public int getHeight() { return height;}
    public Field getField() { return field; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setR(double r) { this.r = r; }
    public void setVelX(double velX) { this.velX = velX; }
    public void setVelY(double velY) { this.velY = velY; }
    public void setVelR(double velR) { this.velR = velR; }
    public void setAccX(double accX) { this.accX = accX; }
    public void setAccY(double accY) { this.accY = accY; }
    public void setAccR(double accR) { this.accR = accR; }
    public void setHealth(double health) { this.health = health;}
    public void setMass(double mass) { this.mass = mass;}

    public final void die() {
        onDeath();
        health = 0;
    }

    public final void appear(int uuid) {
        this.uuid = uuid;
        onBirth();
    }

    public void damage(double amount) {health -= amount;}
    public void heal(double amount) {health += amount;}
    public void add(Entity e) {field.add(e);}

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }
}
